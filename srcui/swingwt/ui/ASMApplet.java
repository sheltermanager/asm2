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
package net.sourceforge.sheltermanager.asm.ui.ui;

import net.sourceforge.sheltermanager.asm.startup.Startup;

import swingwt.awt.*;

import swingwtx.swing.*;


public class ASMApplet extends JApplet {
    public void init() {
        String jdbcurl = getParameter("jdbcurl");
        String user = getParameter("user");

        try {
            SwingUtilities.invokeAndWait(new Startup(new String[0], jdbcurl,
                    user, this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMain(JComponent toolbar, JComponent desktop,
        JComponent status, JMenuBar menu) {
        getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(desktop, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);
        setJMenuBar(menu);
        getContentPane().invalidate();
        getContentPane().validate();
        getContentPane().repaint();
    }

    public void stop() {
    }
}
