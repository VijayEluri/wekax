/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    TrainingSetMaker.java
 *    Copyright (C) 2002 Mark Hall
 *
 */

package weka.gui.beans;

import java.io.Serializable;
import java.util.Vector;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import java.awt.*;

/**
 * Bean that accepts a data sets and produces a training set
 *
 * @author <a href="mailto:mhall@cs.waikato.ac.nz">Mark Hall</a>
 * @version $Revision: 1.1.1.1 $
 */
public class TrainingSetMaker 
  extends AbstractTrainingSetProducer 
  implements DataSourceListener, EventConstraints, Serializable {


  public TrainingSetMaker() {
    super();
    m_visual.setText("TrainingSetMaker");
  }

  /**
   * Accept a data set
   *
   * @param e a <code>DataSetEvent</code> value
   */
  public void acceptDataSet(DataSetEvent e) {
    System.err.println("In accept data set");
    TrainingSetEvent tse = new TrainingSetEvent(this, e.getDataSet());
    tse.m_setNumber = 1;
    tse.m_maxSetNumber = 1;
    notifyTrainingSetProduced(tse);
  }

  /**
   * Inform training set listeners that a training set is availabel
   *
   * @param tse a <code>TrainingSetEvent</code> value
   */
  protected void notifyTrainingSetProduced(TrainingSetEvent tse) {
    Vector l;
    synchronized (this) {
      l = (Vector)m_listeners.clone();
    }
    if (l.size() > 0) {
      for(int i = 0; i < l.size(); i++) {
	System.err.println("Notifying listeners (training set maker)");
	((TrainingSetListener)l.elementAt(i)).acceptTrainingSet(tse);
      }
    }
  }

  /**
   * Stop any action
   */
  public void stop() {
    // do something
  }

  /**
   * Returns true, if at the current time, the named event could
   * be generated. Assumes that supplied event names are names of
   * events that could be generated by this bean.
   *
   * @param eventName the name of the event in question
   * @return true if the named event could be generated at this point in
   * time
   */
  public boolean eventGeneratable(String eventName) {
    if (m_listenee == null) {
      return false;
    }

    if (m_listenee instanceof EventConstraints) {
      if (!((EventConstraints)m_listenee).eventGeneratable("dataSet")) {
	return false;
      }
    }
    return true;
  }
}

