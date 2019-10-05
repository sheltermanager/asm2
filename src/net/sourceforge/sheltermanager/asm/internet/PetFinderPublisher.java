/*
 Animal Shelter Manager
 Copyright(c)2000-2011, R. Rawson-Tetley

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
package net.sourceforge.sheltermanager.asm.internet;

import net.sourceforge.sheltermanager.asm.bo.Animal;
import net.sourceforge.sheltermanager.asm.bo.AnimalVaccination;
import net.sourceforge.sheltermanager.asm.bo.Configuration;
import net.sourceforge.sheltermanager.asm.bo.LookupCache;
import net.sourceforge.sheltermanager.asm.globals.Global;
import net.sourceforge.sheltermanager.asm.ui.internet.InternetPublisher;
import net.sourceforge.sheltermanager.asm.ui.internet.PetFinderMapBreed;
import net.sourceforge.sheltermanager.asm.ui.internet.PetFinderMapSpecies;
import net.sourceforge.sheltermanager.asm.ui.ui.Dialog;
import net.sourceforge.sheltermanager.asm.utility.Utils;
import net.sourceforge.sheltermanager.cursorengine.SQLRecordset;

import java.util.Calendar;


/**
 * The actual class that does the PetFinder publishing work.
 * @author Robin Rawson-Tetley
 */
public class PetFinderPublisher extends FTPPublisher {
    public PetFinderPublisher(InternetPublisher parent,
        PublishCriteria publishCriteria) {
        // Override certain values for petfinder
        publishCriteria.ftpRoot = "";
        publishCriteria.thumbnails = false;

        init("petfinder", parent, publishCriteria,
            Configuration.getString("PetFinderFTPURL"),
            Configuration.getString("PetFinderFTPUser"),
            Configuration.getString("PetFinderFTPPassword"), "21", "");
    }

    public void run() {
        // Before we start, make sure that all species have been
        // mapped:
        if (!checkMappedSpecies()) {
            enableParentButtons();

            return;
        }

        // and breeds...
        if (!checkMappedBreeds()) {
            enableParentButtons();

            return;
        }

        String shelterId = "";

        try {
            shelterId = Configuration.getString("PetFinderFTPUser");
        } catch (Exception e) {
            if (parent != null) {
                Dialog.showError(e.getMessage());
            }

            Global.logException(e, getClass());
            enableParentButtons();

            return;
        }

        // Get a list of animals
        setStatusText(Global.i18n("uiinternet", "retrieving_animal_list"));

        Animal an = null;

        try {
            an = getMatchingAnimals();
        } catch (Exception e) {
            Global.logException(e, getClass());

            if (parent != null) {
                Dialog.showError(e.getMessage());
            } else {
                Global.logError(Global.i18n("uiinternet",
                        "No_matching_animals_were_found_to_publish"),
                    "PetFinderPublisher.run");
                System.exit(1);
            }
        }

        // If there aren't any animals, there's no point do
        // anything
        if (an.size() == 0) {
            if (parent != null) {
                Dialog.showInformation(Global.i18n("uiinternet",
                        "No_matching_animals_were_found_to_publish"));
                enableParentButtons();

                return;
            } else {
                System.exit(1);
            }
        }

        // Open the socket
        if (!openFTPSocket()) {
            if (parent == null) {
                System.exit(1);
            } else {
                enableParentButtons();

                return;
            }
        }

        // Change to the import/photos directory, we
        // do images first and the data file last
        mkdir("import");
        chdir("import");
        mkdir("photos");
        chdir("photos", "import/photos");

        // Start the progress meter
        initStatusBarMax(an.size());

        // Start a new buffer - this is going to be the
        // CSV file required by PetFinder.
        StringBuffer dataFile = new StringBuffer();
        resetStatusBar();
        initStatusBarMax(an.size());

        setStatusText(Global.i18n("uiinternet", "Publishing..."));

        try {
            int anCount = 0;

            while (!an.getEOF()) {
                anCount++;

                Global.logInfo("Processing: " + an.getShelterCode() + ": " +
                    an.getAnimalName() + " (" + anCount + " of " + an.size() +
                    ")", "PetFinderPublisher.run");

                // Upload images
                uploadImages(an, false);

                // Build the CSV file entry for this animal:

                // Animal
                /*
                 * Enumeration of following: | BarnYard | | Bird | | Cat | |
                 * Dog | | Horse | | Pig | | Rabbit | | Reptile | |
                 * Small&Furry |
                 */

                // Look up appropriate mapping from species table
                String pfMap = LookupCache.getSpeciesPetFinderMapping(an.getSpeciesID());
                dataFile.append("\"" + pfMap + "\",");

                // Breed 1
                String pfBMap = LookupCache.getBreedPetFinderMapping(an.getBreedID());
                dataFile.append("\"" + pfBMap + "\",");

                // Age
                /*
                 * -- Enum of | Adult | | Baby | | Senior | | Young |
                 */

                // Take the age of the animal in years - obviously
                // this is going to be a bit out of context as some
                // animals live far longer than others, but we can
                // address that later. Probably need to hold age
                // thresholds for different species somewhere.
                Calendar today = Calendar.getInstance();
                Calendar abday = Utils.dateToCalendar(an.getDateOfBirth());
                double ageInYears = Utils.getDateDiff(today, abday);
                ageInYears = ((((ageInYears / 60) / 24) / 7) / 52);

                String ageName = "Adult";

                if (ageInYears < 0.5) {
                    ageName = "Baby";
                } else if (ageInYears < 2) {
                    ageName = "Young";
                } else if (ageInYears < 9) {
                    ageName = "Adult";
                } else {
                    ageName = "Senior";
                }

                dataFile.append("\"" + ageName + "\",");

                // Name
                dataFile.append("\"" +
                    an.getAnimalName().replaceAll("\"", "\"\"") + "\",");

                // Size
                // -- Enum of S M L XL
                // These can map straight from ASMs default sizes
                // (pretty lucky there!)
                String anSize = "M";

                if (an.getSize().intValue() == 0) {
                    anSize = "XL";
                } else if (an.getSize().intValue() == 1) {
                    anSize = "L";
                } else if (an.getSize().intValue() == 2) {
                    anSize = "M";
                } else if (an.getSize().intValue() == 3) {
                    anSize = "S";
                }

                dataFile.append("\"" + anSize + "\",");

                // Sex
                // -- Can only be M/F
                String sexname = an.getSexName();

                if ((sexname == null) || sexname.trim().equals("")) {
                    sexname = "M";
                } else {
                    sexname = sexname.substring(0, 1);
                }

                dataFile.append("\"" + sexname + "\",");

                // Description
                String comm = an.getWebMediaNotes();

                // No web media, use the animal comments instead
                if (comm.equals("")) {
                    comm = an.getAnimalComments();
                }

                // Add any standard extra text
                comm += Configuration.getString("TPPublisherSig");

                // Use HTML entity line terminators for PF
                comm = comm.replaceAll(new String(new byte[] { 13, 10 }), "&#10;");
                comm = comm.replaceAll("\n", "&#10;");
                comm = comm.replaceAll("\r", "&#10;");
                
                // Escape double quotes
                comm = comm.replaceAll("\"", "\"\"");

                dataFile.append("\"" + comm + "\",");
                comm = null;

                // Special Needs
                if (an.getCrueltyCase().intValue() == 1) {
                    dataFile.append("\"1\",");
                } else if (an.isHasSpecialNeeds().intValue() == 1) {
                    dataFile.append("\"1\",");
                } else {
                    dataFile.append("\"\",");
                }

                // Has Shots
                AnimalVaccination av = new AnimalVaccination();
                av.openRecordset("AnimalID = " + an.getID() +
                    " AND DateOfVaccination Is Not Null");

                if (!av.getEOF()) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                av.free();
                av = null;

                // Altered
                if (an.getNeutered().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // No Dogs
                if (an.isGoodWithDogs().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // No Cats
                if (an.isGoodWithCats().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // No Kids
                if (an.isGoodWithKids().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // No Claws
                if (an.getDeclawed().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // Housebroken
                if (an.isHouseTrained().intValue() == 0) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // Id
                dataFile.append("\"" + an.getShelterCode() + "\",");

                // Breed 2 - only set it for crossbreeds
                String pfBMap2 = "";

                if (an.getCrossBreed().intValue() == 1) {
                    pfBMap2 = LookupCache.getBreedPetFinderMapping(an.getBreed2ID());
                }

                dataFile.append("\"" + pfBMap2 + "\",");

                // Mix flag
                if (an.getCrossBreed().intValue() == 1) {
                    dataFile.append("1,");
                } else {
                    dataFile.append("\"\",");
                }

                // photo1-6 (always blank because we send photos via FTP)
                dataFile.append("\"\",");
                dataFile.append("\"\",");
                dataFile.append("\"\",");
                dataFile.append("\"\",");
                dataFile.append("\"\",");
                dataFile.append("\"\",");

                // Arrival Date
                dataFile.append("\"" + Utils.getSQLDateOnly(an.getDateBroughtIn()) + "\",");

                // Birth Date
                dataFile.append("\"" + Utils.getSQLDateOnly(an.getDateOfBirth()) + "\",");

                // primaryColor
                dataFile.append("\"\",");

                // secondaryColor
                dataFile.append("\"\",");

                // tertiaryColor
                dataFile.append("\"\",");

                // coat_length
                dataFile.append("\"\",");

                // AdoptionFee
                dataFile.append("\"\",");

                // Display adoption fee?
                dataFile.append("\"\",");

                // Adoption fee waived
                dataFile.append("\"\",");

                // Special needs notes
                dataFile.append("\"\",");

                // No other pets?
                dataFile.append("\"\",");

                // No other note
                dataFile.append("\"\",");

                // Tags
                dataFile.append("\"\"");

                // Terminate
                dataFile.append("\n");

                // Mark media records for this animal as published
                markAnimalPublished("LastPublishedPF", an.getID());

                Global.logInfo("Finished processing " + an.getShelterCode(),
                    "PetFinderPublisher.run");

                an.moveNext();
                incrementStatusBar();
            }
        } catch (Exception e) {
            if (parent != null) {
                Dialog.showError(e.getMessage());
            }

            Global.logException(e, getClass());
        }

        // Remove animal object
        an.free();
        an = null;

        // Save the data file to our publish directory
        saveFile(publishDir + shelterId, dataFile.toString());

        // Upload the data file to the site
        chdir("..", "import");

        Global.logInfo("Uploading data", "PetFinderPublisher.run");
        upload(shelterId);
        Global.logInfo("Data uploaded", "PetFinderPublisher.run");

        resetStatusBar();
        setStatusText("");

        // Disconnect from the remote host
        closeFTPSocket();

        // Tell user it's finished
        if (parent != null) {
            Dialog.showInformation(Global.i18n("uiinternet",
                    "petfinder_publishing_complete"),
                Global.i18n("uiinternet", "petfinder_publishing_complete"));
        } else {
            Global.logInfo(Global.i18n("uiinternet",
                    "petfinder_publishing_complete"), "PetFinderPublisher.run");
            System.exit(0);
        }

        // Re-enable buttons
        enableParentButtons();
    }

    /**
     * Verifies that all system species have been mapped to PetFinder types
     * before running
     */
    private boolean checkMappedSpecies() {
        boolean retval = false;

        try {
            SQLRecordset s = new SQLRecordset();
            s.openRecordset("SELECT * FROM species WHERE PetFinderSpecies Is Null OR PetFinderSpecies = ''",
                "species");
            retval = s.getEOF();
            s.free();
            s = null;
        } catch (Exception e) {
            Global.logException(e, getClass());
        }

        if (!retval) {
            if (parent == null) {
                Global.logError(Global.i18n("uiinternet",
                        "not_all_your_petfinder_types_are_mapped"),
                    "PetFinderPublisher.checkMappedSpecies");
                System.exit(1);
            }

            if (Dialog.showYesNo(Global.i18n("uiinternet",
                            "unmapped_petfinder_species"),
                        Global.i18n("uiinternet", "Unmapped_Types"))) {
                Global.mainForm.addChild(new PetFinderMapSpecies());
            }
        }

        return retval;
    }

    /**
     * Verifies that all system breeds have been mapped to PetFinder breeds
     * before running
     */
    private boolean checkMappedBreeds() {
        boolean retval = false;

        try {
            SQLRecordset b = new SQLRecordset();
            b.openRecordset("SELECT * FROM breed WHERE PetFinderBreed Is Null OR PetFinderBreed = ''",
                "breed");
            retval = b.getEOF();
            b.free();
            b = null;
        } catch (Exception e) {
            Global.logException(e, getClass());
        }

        if (!retval) {
            if (parent == null) {
                Global.logError(Global.i18n("uiinternet",
                        "not_all_your_petfinder_breeds_are_mapped"),
                    "PetFinderPublisher.checkMappedBreeds");
                System.exit(1);
            }

            if (Dialog.showYesNo(Global.i18n("uiinternet",
                            "unmapped_petfinder_breeds"),
                        Global.i18n("uiinternet", "Unmapped_Types"))) {
                Global.mainForm.addChild(new PetFinderMapBreed());
            }
        }

        return retval;
    }
}
