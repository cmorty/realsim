package de.fau.cooja.plugins.realsim;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;

import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;

public class MoteTypeComboboxModel extends AbstractListModel<Object> implements Observer, ComboBoxModel<Object> {
	private Simulation				sim;
	protected ArrayList<lMoteType>	mtl			= new ArrayList<lMoteType>();
	protected lMoteType						selected	= null;
	
	MoteTypeComboboxModel(Simulation sim) {
		this.sim = sim;
		sim.addObserver(this);
		update(null, null); //Initialise
	}
	
	@Override
	public int getSize() {
		return mtl.size();
	}
	
	@Override
	public Object getElementAt(int index) {
		return mtl.get(index);
	}
	
	public void setSelectedItem(Object anItem) {
		if(! (anItem instanceof lMoteType)) return;
		selected = (lMoteType) anItem;
		fireContentsChanged(this, -1, -1);
		
	}
	
	public Object getSelectedItem() {
		// TODO Auto-generated method stub
		return selected;
	}
	
	public MoteType getSelectedMote() {
		// TODO Auto-generated method stub
		return selected.getMoteType();
	}
	
	public void update(Observable obj, Object arg1) {
		int cnt = 0;
		for (MoteType mt : sim.getMoteTypes()) {
			if (mtl.contains(new lMoteType(mt)))
				continue;
			mtl.add(new lMoteType(mt));
			cnt++;
		}
		
		if (selected == null && getSize() > 0) {
			selected = (lMoteType) getElementAt(0);
		}
		
		fireIntervalAdded(this, mtl.size() - cnt, mtl.size() - 1);
		
	}
	
	class lMoteType {
		MoteType	t;
		
		lMoteType(MoteType t) {
			this.t = t;
		}
		
		@Override
		public String toString() {
			return t.getDescription();
		}
		
		MoteType getMoteType() {
			return t;
		}
		
		@Override
		public int hashCode() {
			return t.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof lMoteType){
				return (t == ((lMoteType)o).getMoteType());
			}
			if(o instanceof MoteType){
				return (t == (MoteType)o);
			}
			
			return false;
		}
		
		
	}
	
}
