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

package net.rrm.ehour.ui.common;

import net.rrm.ehour.timesheet.service.IOverviewTimesheet;
import net.rrm.ehour.ui.common.session.EhourWebSession;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.notNull;

/**
 * Mock expectations
 */

public class MockExpectations {
    private MockExpectations() {

    }

    @SuppressWarnings("deprecation")
    public static void navCalendar(IOverviewTimesheet IOverviewTimesheet, TestEhourWebApplication webApp) {
        Calendar requestedMonth = new GregorianCalendar(2007, 12 - 1, 10);
        EhourWebSession session = webApp.getSession();
        session.setNavCalendar(requestedMonth);

        LocalDate bookedDay = new LocalDate(2007, DateTimeConstants.DECEMBER, 15);

        expect(IOverviewTimesheet.getBookedDaysMonthOverview((Integer) notNull(), (Calendar) notNull()))
                .andReturn(Arrays.asList(bookedDay));
    }
}
