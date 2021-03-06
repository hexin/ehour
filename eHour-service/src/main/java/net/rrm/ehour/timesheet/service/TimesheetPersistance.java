/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package net.rrm.ehour.timesheet.service;

import net.rrm.ehour.audit.annot.NonAuditable;
import net.rrm.ehour.data.DateRange;
import net.rrm.ehour.domain.ProjectAssignment;
import net.rrm.ehour.domain.TimesheetComment;
import net.rrm.ehour.domain.TimesheetEntry;
import net.rrm.ehour.domain.User;
import net.rrm.ehour.exception.OverBudgetException;
import net.rrm.ehour.mail.service.MailService;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetCommentDao;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetDao;
import net.rrm.ehour.project.status.ProjectAssignmentStatus;
import net.rrm.ehour.project.status.ProjectAssignmentStatusService;
import net.rrm.ehour.util.EhourConstants;
import net.rrm.ehour.util.EhourUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TimesheetPersistance implements IPersistTimesheet, IDeleteTimesheetEntry {
    private static final Logger LOGGER = Logger.getLogger(TimesheetPersistance.class);

    private TimesheetDao timesheetDAO;
    private TimesheetCommentDao timesheetCommentDAO;
    private ProjectAssignmentStatusService projectAssignmentStatusService;
    private MailService mailService;
    private ApplicationContext context;

    @Autowired
    public TimesheetPersistance(TimesheetDao timesheetDAO, TimesheetCommentDao timesheetCommentDAO, ProjectAssignmentStatusService projectAssignmentStatusService, MailService mailService, ApplicationContext context) {
        this.timesheetDAO = timesheetDAO;
        this.timesheetCommentDAO = timesheetCommentDAO;
        this.projectAssignmentStatusService = projectAssignmentStatusService;
        this.mailService = mailService;
        this.context = context;
    }

    @Transactional
    public void deleteAllTimesheetDataForUser(User user) {
        timesheetCommentDAO.deleteCommentsForUser(user.getUserId());

        if (user.getProjectAssignments() != null && user.getProjectAssignments().size() > 0) {
            timesheetDAO.deleteTimesheetEntries(EhourUtil.getIdsFromDomainObjects(user.getProjectAssignments()));
        }
    }

    @Transactional
    public List<ProjectAssignmentStatus> persistTimesheetWeek(Collection<TimesheetEntry> timesheetEntries,
                                                              TimesheetComment comment,
                                                              DateRange weekRange) {
        Map<ProjectAssignment, List<TimesheetEntry>> timesheetRows = getTimesheetAsRows(timesheetEntries);

        List<ProjectAssignmentStatus> errorStatusses = new ArrayList<ProjectAssignmentStatus>();

        for (Map.Entry<ProjectAssignment, List<TimesheetEntry>> entry : timesheetRows.entrySet()) {
            try {
                getSpringProxy().validateAndPersist(entry.getKey(), entry.getValue(), weekRange);
            } catch (OverBudgetException e) {
                errorStatusses.add(e.getStatus());
            }
        }

        if (comment.getNewComment() == Boolean.FALSE || StringUtils.isNotBlank(comment.getComment())) {
            timesheetCommentDAO.persist(comment);
        }

        return errorStatusses;
    }

    private IPersistTimesheet getSpringProxy() {
        return context.getBean(IPersistTimesheet.class);
    }

    private Map<ProjectAssignment, List<TimesheetEntry>> getTimesheetAsRows(Collection<TimesheetEntry> entries) {
        Map<ProjectAssignment, List<TimesheetEntry>> timesheetRows = new HashMap<ProjectAssignment, List<TimesheetEntry>>();

        for (TimesheetEntry timesheetEntry : entries) {
            ProjectAssignment assignment = timesheetEntry.getEntryId().getProjectAssignment();

            List<TimesheetEntry> assignmentEntries = (timesheetRows.containsKey(assignment))
                    ? timesheetRows.get(assignment)
                    : new ArrayList<TimesheetEntry>();

            assignmentEntries.add(timesheetEntry);

            timesheetRows.put(assignment, assignmentEntries);
        }

        return timesheetRows;
    }

    @Transactional(rollbackFor = OverBudgetException.class, propagation = Propagation.REQUIRES_NEW)
    @NonAuditable
    public void validateAndPersist(ProjectAssignment assignment,
                                   List<TimesheetEntry> entries,
                                   DateRange weekRange) throws OverBudgetException {
        ProjectAssignmentStatus beforeStatus = projectAssignmentStatusService.getAssignmentStatus(assignment);

        boolean checkAfterStatus = beforeStatus.isValid();

        try {
            persistEntries(assignment, entries, weekRange, !beforeStatus.isValid());
        } catch (OverBudgetException obe) {
            // make sure it's retrown by checking the after status
            checkAfterStatus = true;
        }

        ProjectAssignmentStatus afterStatus = projectAssignmentStatusService.getAssignmentStatus(assignment);

        if (checkAfterStatus && !afterStatus.isValid()) {
            throw new OverBudgetException(afterStatus);
        } else if (!beforeStatus.equals(afterStatus) && canNotifyPm(assignment)) {
            notifyPm(assignment, afterStatus);
        }
    }

    private void persistEntries(ProjectAssignment assignment, List<TimesheetEntry> entries, DateRange weekRange, boolean onlyLessThanExisting) throws OverBudgetException {
        List<TimesheetEntry> previousEntries = timesheetDAO.getTimesheetEntriesInRange(assignment, weekRange);

        for (TimesheetEntry entry : entries) {
            if (!entry.getEntryId().getProjectAssignment().equals(assignment)) {
                LOGGER.error("Invalid entry in assignment list, skipping: " + entry);
                continue;
            }

            if (entry.isEmptyEntry()) {
                deleteEntry(getEntry(previousEntries, entry));
            } else {
                persistEntry(onlyLessThanExisting, entry, getEntry(previousEntries, entry));
            }

            previousEntries.remove(entry);
        }

        removeOldEntries(previousEntries);
    }

    private void removeOldEntries(List<TimesheetEntry> previousEntries) {
        for (TimesheetEntry entry : previousEntries) {
            timesheetDAO.delete(entry);
        }
    }

    private void deleteEntry(TimesheetEntry existingEntry) {
        if (existingEntry != null) {
            timesheetDAO.delete(existingEntry);
        }
    }

    private void persistEntry(boolean onlyLessThanExisting, TimesheetEntry newEntry, TimesheetEntry existingEntry) throws OverBudgetException {
        if (onlyLessThanExisting &&
                (existingEntry == null ||
                        (newEntry.getHours().compareTo(existingEntry.getHours()) > 0))) {
            throw new OverBudgetException();
        }

        newEntry.setUpdateDate(new Date());

        if (existingEntry != null) {
            timesheetDAO.merge(newEntry);
        } else {
            timesheetDAO.persist(newEntry);
        }
    }

    private TimesheetEntry getEntry(List<TimesheetEntry> entries, TimesheetEntry entry) {
        int index = entries.indexOf(entry);

        if (index >= 0) {
            return entries.get(index);
        } else {
            return null;
        }
    }

    private void notifyPm(ProjectAssignment assignment, ProjectAssignmentStatus status) {
        TimesheetEntry entry;

        entry = timesheetDAO.getLatestTimesheetEntryForAssignment(assignment.getAssignmentId());

        // over alloted - fixed
        if (assignment.getAssignmentType().getAssignmentTypeId() == EhourConstants.ASSIGNMENT_TIME_ALLOTTED_FIXED
                && status.getStatusses().contains(ProjectAssignmentStatus.Status.OVER_ALLOTTED)) {
            mailService.mailPMFixedAllottedReached(status.getAggregate(),
                    entry.getEntryId().getEntryDate(),
                    assignment.getProject().getProjectManager());
        }
        // over overrun - flex
        else if (assignment.getAssignmentType().getAssignmentTypeId() == EhourConstants.ASSIGNMENT_TIME_ALLOTTED_FLEX
                && status.getStatusses().contains(ProjectAssignmentStatus.Status.OVER_OVERRUN)) {
            mailService.mailPMFlexOverrunReached(status.getAggregate(),
                    entry.getEntryId().getEntryDate(),
                    assignment.getProject().getProjectManager());
        }
        // in overrun - flex
        else if (status.getStatusses().contains(ProjectAssignmentStatus.Status.IN_OVERRUN)
                && assignment.getAssignmentType().getAssignmentTypeId() == EhourConstants.ASSIGNMENT_TIME_ALLOTTED_FLEX) {
            mailService.mailPMFlexAllottedReached(status.getAggregate(),
                    entry.getEntryId().getEntryDate(),
                    assignment.getProject().getProjectManager());
        }
    }

    private boolean canNotifyPm(ProjectAssignment assignment) {
        return assignment.isNotifyPm()
                && assignment.getProject().getProjectManager() != null
                && assignment.getProject().getProjectManager().getEmail() != null;

    }
}
