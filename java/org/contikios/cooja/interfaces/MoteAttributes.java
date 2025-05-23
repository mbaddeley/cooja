/*
 * Copyright (c) 2010, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.interfaces;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.plugins.skins.AttributeVisualizerSkin;

/**
 * MoteAttributes used to store mote attributes for debugging and statistics
 * The interface is write-only: the simulated Contiki has no knowledge of current attributes
 * with other motes. The Contiki application can, however, add and remove attributes.
 *
 * A Contiki application adds/removes a relation by outputting a simple messages on its log interface,
 * typically via printf()'s of the serial port.
 *
 * Mote attributes are visualized by {@link AttributeVisualizerSkin}.
 *
 * Syntax:
 * "#A <Attribute Name>=<Attribute Value>"
 * "#A <Attribute Name>=<Attribute Value>;<Color>"
 *
 * Example, add an attribute 'sent' with value 41:
 * "#A sent=41"
 *
 * Example, add an attribute 'sent' with value 41, visualized in red:
 * "#A sent=41;RED"
 *
 * Example, remove attribute 'sent' (if any):
 * "#A sent"
 *
 * Special attribute example, visualizes mote in red:
 * "#A color=RED"
 *
 * @see AttributeVisualizerSkin
 * @author Joakim Eriksson
 */
@ClassDescription("Mote Attributes")
public class MoteAttributes extends MoteInterface {
  private static Logger logger = Logger.getLogger(MoteAttributes.class);
  private Mote mote = null;

  private HashMap<String, Object> attributes = new HashMap<String, Object>();

  private Observer logObserver = new Observer() {
    public void update(Observable o, Object arg) {
      String msg = ((Log) o).getLastLogMessage();
      handleNewLog(msg);
    };
  };

  public MoteAttributes(Mote mote) {
    this.mote = mote;
  }

  public void added() {
    super.added();

    /* Observe log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log) {
        ((Log)mi).addObserver(logObserver);
      }
    }
  }

  public void removed() {
    super.removed();

    /* Stop observing log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log) {
        ((Log)mi).deleteObserver(logObserver);
      }
    }
    logObserver = null;
  }

  private void handleNewLog(String msg) {
    if (msg == null) {
      return;
    }

    if (msg.startsWith("DEBUG: ")) {
      msg = msg.substring("DEBUG: ".length());
    }

    if (msg.startsWith("#A ")) {
      /* remove "#A " */
      msg = msg.substring(3);

      setAttributes(msg);

      setChanged();
      notifyObservers();

    } else if (msg.startsWith("#O ")) {
      /* #O 1,<attributes>*/
      /* remove "#O " */
      msg = msg.substring(3);
      /* Get mote id */
      String moteString = "";
      int idIndex = msg.indexOf(',');
      if (idIndex > 0) {
         moteString = msg.substring(0, idIndex).trim();
         msg = msg.substring(idIndex + 1).trim();
      }

      logger.info("Mote msg: " + msg);
      logger.info("Mote String: " + moteString);

      int moteID;
      try {
        moteID = Integer.parseInt(moteString);
      } catch (Exception e) {
        logger.warn("Not a mote ID!");
        return;
      }

      logger.info("Mote ID: " + moteID);

      /* Locate other mote */
      Mote otherMote = mote.getSimulation().getMoteWithID(moteID);
      if (otherMote == null) {
        logger.warn("No other mote with ID: " + moteID);
        return;
      }
      if (otherMote == mote) {
        logger.warn("Other mote is ourselves?");
        return;
      }

      logger.info("Mote attr: " + msg);

      /* Set the other mote attributes */
      MoteAttributes otherMoteAttributes;
      otherMoteAttributes= otherMote.getInterfaces().getMoteAttributes();
      otherMoteAttributes.setAttributes(msg);
      otherMoteAttributes.setChanged();
      otherMoteAttributes.notifyObservers();

    } else {
      return;
    }
  }

  public void setAttributes(String att) {
    if (att.indexOf(",") >= 0) {
      /* Handle each attribute separately */
      String[] atts = att.split(",");
      for (int i = 0; i < atts.length; i++) {
        setAttributes(atts[i]);
      }
      return;
    }

    String[] args = att.split("=");
    if (args.length == 2) {
      attributes.put(args[0], args[1]);
    } else if (args.length == 1) {
      attributes.remove(args[0]);
    } else {
      /* ignore */
      logger.warn(mote + ": Malformed attribute was ignored: " + att);
    }
  }

  public String getText() {
      StringBuffer sb = new StringBuffer();
      Object[] keys = attributes.keySet().toArray();
      for (int i = 0; i < keys.length; i++) {
          sb.append(keys[i]).append("=").append(attributes.get(keys[i])).append("\n");
      }
      return sb.toString();
  }

  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    final JTextArea attributes = new JTextArea();
    attributes.setEditable(false);
    panel.add(attributes);
    attributes.setText(getText());

    Observer observer;
    this.addObserver(observer = new Observer() {
      public void update(Observable obs, Object obj) {
          attributes.setText(getText());
      }
    });

    // Saving observer reference for releaseInterfaceVisualizer
    panel.putClientProperty("intf_obs", observer);

    return panel;
  }

  public void releaseInterfaceVisualizer(JPanel panel) {
    Observer observer = (Observer) panel.getClientProperty("intf_obs");
    if (observer == null) {
      logger.fatal("Error when releasing panel, observer is null");
      return;
    }
    this.deleteObserver(observer);
  }

  public Collection<Element> getConfigXML() {
    return null;
  }

  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
  }

}
