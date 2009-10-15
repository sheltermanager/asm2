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
package net.sourceforge.sheltermanager.asm.ui.animal;

import net.sourceforge.sheltermanager.asm.bo.AnimalVaccination;
import net.sourceforge.sheltermanager.asm.globals.Global;
import net.sourceforge.sheltermanager.asm.ui.ui.ASMSelector;
import net.sourceforge.sheltermanager.asm.ui.ui.Dialog;
import net.sourceforge.sheltermanager.asm.ui.ui.IconManager;
import net.sourceforge.sheltermanager.asm.ui.ui.UI;
import net.sourceforge.sheltermanager.asm.utility.Utils;
import net.sourceforge.sheltermanager.cursorengine.CursorEngineException;

import java.util.Vector;


public class VaccinationSelector extends ASMSelector
    implements VaccinationParent {
    private int animalid = 0;
    private String[][] vaccinationtabledata = null;
    private boolean hasVacc = false;
    private UI.Button btnAdd;
    private UI.Button btnEdit;
    private UI.Button btnDelete;

    public VaccinationSelector() {
        init("uianimal", false);
    }

    public Object getDefaultFocusedComponent() {
        return getTable();
    }

    public Vector getTabOrder() {
        Vector ctl = new Vector();
        ctl.add(getTable());

        return ctl;
    }

    public boolean hasData() {
        return hasVacc;
    }

    public void setLink(int animalid, int linkType) {
        this.animalid = animalid;
    }

    public void setSecurity() {
        if (!Global.currentUserObject.getSecAddAnimalVaccination()) {
            btnAdd.setEnabled(false);
        }

        if (!Global.currentUserObject.getSecChangeAnimalVaccination()) {
            btnEdit.setEnabled(false);
            disableDoubleClick = true;
        }

        if (!Global.currentUserObject.getSecDeleteAnimalVaccination()) {
            btnDelete.setEnabled(false);
        }
    }

    public void tableClicked() {
    }

    public void tableDoubleClicked() {
        actionEdit();
    }

    public void updateList() {
        hasVacc = false;

        AnimalVaccination animalvaccinations = new AnimalVaccination();

        try {
            animalvaccinations.openRecordset("AnimalID = " + animalid);
        } catch (Exception e) {
            Dialog.showError(Global.i18n("uianimal",
                    "Unable_to_open_animal_vaccination_records:_") +
                e.getMessage(), i18n("Error"));
            Global.logException(e, getClass());
        }

        // Create an array to hold the results for the table - note that we
        // have an extra column on here - the last column will actually hold
        // the ID.
        vaccinationtabledata = new String[(int) animalvaccinations.getRecordCount()][5];

        // Create an array of headers for the accounts
        String[] columnheaders = {
                i18n("Type"), i18n("Required"), i18n("Given"), i18n("Comments")
            };

        // loop through the data and fill the array
        int i = 0;

        try {
            hasVacc = !animalvaccinations.getEOF();

            while (!animalvaccinations.getEOF()) {
                vaccinationtabledata[i][0] = animalvaccinations.getVaccinationTypeName();

                try {
                    vaccinationtabledata[i][1] = Utils.formatTableDate(animalvaccinations.getDateRequired());
                    vaccinationtabledata[i][2] = Utils.formatTableDate(animalvaccinations.getDateOfVaccination());
                } catch (Exception e) {
                }

                vaccinationtabledata[i][3] = Utils.nullToEmptyString(animalvaccinations.getComments());
                vaccinationtabledata[i][4] = animalvaccinations.getID()
                                                               .toString();
                i++;
                animalvaccinations.moveNext();
            }
        } catch (CursorEngineException e) {
            Dialog.showError(Global.i18n("uianimal",
                    "Unable_to_read_vaccination_records:_") + e.getMessage(),
                i18n("Error"));
            Global.logException(e, getClass());
        }

        setTableData(columnheaders, vaccinationtabledata, i, 4);
    }

    public void updateVaccinations() {
        updateList();
    }

    public void addToolButtons() {
        btnAdd = UI.getButton(null, i18n("New_vaccination_record"), 'n',
                IconManager.getIcon(IconManager.SCREEN_EDITVACCINATIONS_NEW),
                UI.fp(this, "actionAdd"));
        addToolButton(btnAdd, false);

        btnEdit = UI.getButton(null,
                i18n("Edit_the_highlighted_vaccination_record"), 'e',
                IconManager.getIcon(IconManager.SCREEN_EDITVACCINATIONS_EDIT),
                UI.fp(this, "actionEdit"));
        addToolButton(btnEdit, true);

        btnDelete = UI.getButton(null,
                i18n("Delete_the_highlighted_vaccination_record"), 'd',
                IconManager.getIcon(IconManager.SCREEN_EDITVACCINATIONS_DELETE),
                UI.fp(this, "actionDelete"));
        addToolButton(btnDelete, true);
    }

    public void actionAdd() {
        VaccinationEdit ea = new VaccinationEdit(this);

        try {
            ea.openForNew(animalid);
            Global.mainForm.addChild(ea);
            ea = null;
        } catch (Exception e) {
            Dialog.showError(i18n("Unable_to_create_new_vaccination:_") +
                e.getMessage(), i18n("Error"));
            Global.logException(e, getClass());
        }
    }

    public void actionEdit() {
        int id = getTable().getSelectedID();

        if (id == -1) {
            return;
        }

        // Create a new EditAnimalVaccination screen
        VaccinationEdit ea = new VaccinationEdit(this);

        // Kick it off into edit mode, passing the ID
        ea.openForEdit(id);

        // Attach it to the main screen
        Global.mainForm.addChild(ea);
        ea = null;
    }

    public void actionDelete() {
        // Make sure they are sure about this
        String message = "";

        if (getTable().getSelectedRowCount() == 1) {
            message = Global.i18n("uianimal",
                    "You_are_about_to_permanently_delete_this_record,_are_you_sure_you_wish_to_do_this?");
        } else {
            message = Global.i18n("uianimal",
                    "you_are_about_to_permanently_delete_the_selected_medical_records_are_you_sure");
        }

        if (Dialog.showYesNo(message, Global.i18n("uianimal", "Really_Delete?"))) {
            // Read the highlighted table records and get the IDs
            int[] selrows = getTable().getSelectedRows();

            for (int i = 0; i < selrows.length; i++) {
                // Get the ID for this row
                String avID = vaccinationtabledata[selrows[i]][4];

                // Remove it from the database
                try {
                    String s = "DELETE FROM animalvaccination WHERE ID = " +
                        avID;
                    net.sourceforge.sheltermanager.cursorengine.DBConnection.executeAction(s);
                } catch (Exception e) {
                    Dialog.showError(Global.i18n("uianimal",
                            "An_error_occurred_while_deleting_the_record:_") +
                        e.getMessage(), Global.i18n("uianimal", "Error"));
                    Global.logException(e, getClass());
                }
            }

            updateList();
        }
    }
}