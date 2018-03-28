#!/bin/sh
cd `dirname $0`

# Copy the nsisunz.dll file to the NSIS installation
cp -f nsisunz.dll "$HOME/.wine/drive_c/Program Files (x86)/NSIS/Plugins"

# Make a copy of the asm.nsi file and set the version number on it
cat asm.nsi | sed "s/ZZZasmversionZZZ/`cat ../../VERSION`/g" > build.nsi
wine "$HOME/.wine/drive_c/Program Files (x86)/NSIS/makensis.exe" build.nsi
rm build.nsi
