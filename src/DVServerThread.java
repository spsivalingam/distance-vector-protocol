import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.ConcurrentLinkedQueue;

class DVServerThread extends Thread {
	public final double INFINITY = 99999.0;
	private int BUFFER_SIZE = 32768;
	private int port;
	private String file;
	// DatagramSocket on server
	protected DatagramSocket serverSocket = null; 
	private boolean listening = true;
	Hashtable<String, Double> neighbor_pair = null;		
	// Hashtable<String, Integer> neighbors = null;
	ArrayList<String> neighbors = null;
	// LinkedList<DistanceVector[]> currentHost = null;
	Hashtable<String, Vector<DistanceVector>> currentHost = null;
  
    public DVServerThread(int port, String file, Hashtable<String, Double> neighbor_pair, ArrayList<String> neighbors, Hashtable<String, Vector<DistanceVector>> currentHost) throws IOException {
        super("DVServerThread");
		this.port = port;
		this.file = file;        
		serverSocket = new DatagramSocket(port);
		
		this.neighbor_pair = neighbor_pair;
		this.neighbors = neighbors;
		this.currentHost = currentHost;		
    }
 
    public void run() { 
        while(listening) {
        	RoutingTable rt;
			try {
				// listen on request as server
				
                byte[] bufReceived = new byte[BUFFER_SIZE];                
                DatagramPacket serverPacket = new DatagramPacket(bufReceived, bufReceived.length);
                serverSocket.receive(serverPacket); 
                // parse received info.
				String received = new String(serverPacket.getData(), 0, serverPacket.getLength());
				// System.out.println("Received from " + serverPacket.getAddress().getHostName() + ":\n" + received);
				LinkedList<RoutingTable> receivedList = readReceived(received);
				
				Hashtable<String, Vector<DistanceVector>> latest = currentHost;
				// current host source
				String[] hostFile = file.split("\\.dat");
				String start = hostFile[0];
				for(int i=0; i<receivedList.size(); i++) {
					rt = receivedList.get(i);
					// update cost
					if(latest.containsKey(start + "-" + rt.getDest())) {
						Vector<DistanceVector> dVector = latest.get(start + "-" + rt.getDest());
						int index = neighbors.indexOf(rt.getSource());
						double newCost = neighbor_pair.get(start + "-" + rt.getSource()) + rt.getCost();
						if(dVector.get(index).getCost() > newCost) {
							dVector.get(index).setCost(newCost);
							// System.out.println("Cost change to currentHost");
						}
					}
					else {
						if(start.equals(rt.getDest())) {
							
							continue;
						}
						
						else {
							Vector<DistanceVector> newVector = new Vector<DistanceVector>();							
							for(int k=0; k<neighbors.size(); k++) {
								newVector.add(new DistanceVector(start, neighbors.get(k), rt.getDest(), INFINITY));
							}
							int index = neighbors.indexOf(rt.getSource());
							// in-place replace
							newVector.set(index, new DistanceVector(start, rt.getSource(), rt.getDest(), neighbor_pair.get(start + "-" + rt.getSource()) + rt.getCost()));
							currentHost.put(start + "-" + rt.getDest(), newVector);
							// System.out.println("Added newLine into currentHost");
						}						
					}
				}			
				
            } 
			catch(IOException e) {
                e.printStackTrace();
            }			
        }
        serverSocket.close();
    }
	
	
	private LinkedList<RoutingTable> readReceived(String received) {
		//System.out.println("Entered the readReceived"+received);
		//String receivedRegex = "(?:([A-Za-z\\s]+node ))([A-Za-z\\d\\.]+)(?:([A-Za-z\\s]+node ))([A-Za-z\\d\\.]+):(?:([A-Za-z\\s]+node ))([A-Za-z\\d\\.]+)(?:([A-Za-z\\s]+)+)([\\d.]+)";
	//	String receivedRegex = "(?:([A-Za-z\\s]+node ))([\\d]+)(?:([A-Za-z\\s]+node ))(([\\d:]+)):(?:([A-Za-z\\s]+node ))([\\d]+)(?:([A-Za-z\\s]+)+)([\\d.]+)";
	//	Pattern receivedPattern = Pattern.compile(receivedRegex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
		// return rtList
		LinkedList<RoutingTable> rtList = new LinkedList<RoutingTable>();
	//	System.out.println("REGEX MATCH :"+received.matches(receivedRegex));
		String[] lines = received.split("\n");			
		for(int i=0; i<lines.length; i++) {
			//Matcher receivedMatch = receivedPattern.matcher(lines[i]);
			String [] line = lines[i].split(";");
			String start = "";
			String end = "";
			String via = "";
			String cost = "";
			if(line.length >=4) {			
				start =  line[0];         //receivedMatch.group(2);
				end =    line[1];          //receivedMatch.group(4);
				via =    line[2];           //receivedMatch.group(6);
				cost =   line[3];            //receivedMatch.group(8);
			}
			//System.out.println("The cost inside readreceived"+cost);
			// System.out.println(start + "--" + via + "--" + end + "  " + cost + "\n");
			rtList.add(new RoutingTable(start, via, end, Double.parseDouble(cost)));			
		}
		return rtList;
	}
		
	// find min then convert RT
	// DVToRoutingTable to send	
	private String DVToRT(LinkedList<DistanceVector[]> dvList) {
		Double min = Double.MAX_VALUE;
		String group1 = "shortest path from node ";
		String group3 = " to node ";
		String group5 = ": the next hop is node ";
		String group7 = " and the cost is ";
		
		String res = "";		
		for(int i=0; i<dvList.size(); i++) {
			DistanceVector[] dvArray = dvList.get(i);
			double[] costs = new double[dvArray.length];
			String source = "";
			String dest = "";
			String via = "";
			// populate costs double array and find min index 
			for(int j=0; j<dvArray.length; j++) {
				costs[j] = dvArray[j].getCost();				
			}
			source = dvArray[findMinIndex(costs)].getSource();
			dest = dvArray[findMinIndex(costs)].getDest();
			via = dvArray[findMinIndex(costs)].getVia();
			min = dvArray[findMinIndex(costs)].getCost();		
			
			res += group1 + source + group3 + dest + group5 + via + group7 + min + "\n";
		}
		
		return res;			
	}
	
	private int findMinIndex(double[] d) {
		double min = Double.MAX_VALUE;
		for(int i=0; i<d.length; i++) {
			if(min > d[i]) {
				min = d[i];
			}
		}
		
		for(int i=0; i<d.length; i++) {
			if(min == d[i]) {
				return i;
			}
		}
		return -1;
	}
}