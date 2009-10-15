/*
 Animal Shelter Manager
 Copyright(c)2000-2009, R. Rawson-Tetley

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation; either version 2 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTIBILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the
 Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston
 MA 02111-1307, USA.

 Contact me by electronic mail: bobintetley@users.sourceforge.net
 */
package net.sourceforge.sheltermanager.asm.reports;

import net.sourceforge.sheltermanager.asm.globals.Global;
import net.sourceforge.sheltermanager.asm.utility.Utils;


/**
 * Generates a report from animal search results.
 *
 */
public class SearchResults extends Report {
    private String[][] searchResults = null;
    private int max = 0;

    public SearchResults(String[][] theSearchResults, int max) {
        this.max = max;
        searchResults = theSearchResults;

        if (theSearchResults == null) {
            return;
        }

        this.start();
    }

    public String getTitle() {
        return Global.i18n("reports", "Animal_Search_Results_",
            Utils.getReadableTodaysDate());
    }

    public void generateReport() {
        // For reporting purposes, substitute the report name
        // for the normal animal name (column 13 to column 0). This
        // just flips them around, and we do it again at the end.
        // This is because the table relies on this information
        // too and switching these columns causes HTML to appear on
        // the search form
        flipNames();

        tableNew();

        String[] headers = {
                Global.i18n("reports", "Animal_Name"),
                Global.i18n("reports", "Short_Code"),
                Global.i18n("reports", "Internal_Location"),
                Global.i18n("reports", "Species"),
                Global.i18n("reports", "Breed"), Global.i18n("reports", "Sex"),
                Global.i18n("reports", "Age"), Global.i18n("reports", "Size"),
                Global.i18n("reports", "Colour"),
                Global.i18n("reports", "Features"),
                Global.i18n("reports", "Identichip_No"),
                Global.i18n("reports", "Date_Brought_In")
            };

        tableAddRow();

        for (int i = 0; i < headers.length; i++) {
            tableAddCell(bold(headers[i]));
        }

        tableFinishRow();

        setStatusBarMax(max);

        for (int i = 0; i < max; i++) {
            tableAddRow();

            for (int z = 0; z < 12; z++) {
                tableAddCell(searchResults[i][z]);
            }

            tableFinishRow();
            incrementStatusBar();
        }

        tableFinish();
        addTable();

        // Flip the names back again so the search form goes back to
        // normal and so we can do the report again.
        flipNames();
    }

    private void flipNames() {
        String temp = "";

        for (int i = 0; i < max; i++) {
            temp = searchResults[i][0];
            searchResults[i][0] = searchResults[i][13];
            searchResults[i][13] = temp;
        }
    }
}