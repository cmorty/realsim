package src;

// Help class
public class MyEdge {
	
	private int src;
	private int dst;
	
	public MyEdge(int src, int dst){
		this.src = src;
		this.dst = dst;
	}
	
	public int getSrc(){
		return this.src;
	}
	
	public int getDst(){
		return this.dst;
	}
	
	public boolean equals(MyEdge e){
		return (this.getSrc() == e.getSrc() &&  this.getDst() == e.getDst()) ? true : false;
	}
}
