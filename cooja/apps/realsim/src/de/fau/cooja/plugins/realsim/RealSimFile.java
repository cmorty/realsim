package de.fau.cooja.plugins.realsim;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import se.sics.cooja.GUI;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.radiomediums.DirectedGraphMedium;

public class RealSimFile extends VisPlugin implements ActionListener, Observer {
	protected Simulation		sim;
	public JPanel				controlPanel		= new JPanel();
	JTextField					filename			= new JTextField();
	JToggleButton				select_file			= new JToggleButton("Open File");
	
	public RealSimFile(Simulation simulation, GUI gui) {
		super("RealSim", gui, false);
		this.sim = simulation;
	}
	
	public void startPlugin() {
		//Do not start if we do not support the medium
		if(!(sim.getRadioMedium() instanceof DirectedGraphMedium)) return;

		add("Center", controlPanel);
		controlPanel.add(filename);
		filename.addActionListener(this);
		controlPanel.add(select_file);
		select_file.addActionListener(this);
		sim.addObserver(this);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(180, 190);
		this.setLocation(320, 0);
		this.setBackground(Color.WHITE);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == select_file) {
			JFileChooser fc = new JFileChooser();
		    int fcrv = fc.showOpenDialog(controlPanel);
		    if(fcrv == JFileChooser.APPROVE_OPTION) {
		       filename.setText(fc.getSelectedFile().getName());
		    }
		}
		
	}

	@Override
	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub
		
	}

	
	
}
