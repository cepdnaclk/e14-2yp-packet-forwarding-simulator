package com.co227.project.packetForwadingSimulator.simulators;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;



public class Simulator {
	public  int noRouters = 0,noLinks;
	private int[][] adjecencyMat = null,forwardingTable;
	private boolean settingTopology=false;
	public static HashMap<String,Router>Routers;
	public static HashMap<String,Link>Links;
	public static HashMap<String,Packet>Packets;
	public static HashMap<String,Queue>InputBuffer;
	public static HashMap<String,Queue>OutputBuffer;
	public static boolean injectDone = true;
	public static double timeElapsed=0;
	Simulator(){
		InputBuffer = new HashMap<String,Queue>();
		OutputBuffer = new HashMap<String,Queue>();
		Links = new HashMap<String,Link>();
		Routers = new HashMap<String,Router>();
		this.setUpForwadingTable();
		Packets = new HashMap<String,Packet>();
		gui227 inputWindow= new gui227();
		
		this.start();
	}
	private void start() {
		while(true){
			this.iteratePackets();
		}
		
	}
	private void iteratePackets() {
		
		//set new event
		for (HashMap.Entry<String, Packet> entry2 : Packets.entrySet())
		{
//		    System.out.println(entry.getKey() + "/" + entry.getValue());
			boolean livingPacket = entry2.getValue().getPacketState();
			if(entry2.getValue().getNextEvent()==null && livingPacket){
				//System.out.println("event is null");
				this.processPacketNextEvent(entry2.getKey());
			}
		}
		//check for next possible event
		double leastEventTime=Double.MAX_VALUE;
		
		for (HashMap.Entry<String, Packet> entry2 : Packets.entrySet())
		{
			
			boolean livingPacket = entry2.getValue().getPacketState();
			if(livingPacket){
				double eventTime = entry2.getValue().getNextEvent().getTimeTaken();
				if(eventTime < leastEventTime){
					leastEventTime = eventTime;
				//	System.out.println("leastEventTime "+leastEventTime);
				}
			}
			
		}
		//excute the events
		if(leastEventTime<Double.MAX_VALUE){
			timeElapsed+=leastEventTime;
		//	System.out.println("################################");
			for (HashMap.Entry<String, Packet> entry2 : Packets.entrySet())
			{
				boolean livingPacket = entry2.getValue().getPacketState();
				if(livingPacket){
					this.executeNextEvent(entry2.getKey(),leastEventTime);
				}
				
			}
			System.out.println("################################## time: "+timeElapsed);
		}
		//check for new injected packets
		gui227.addNewPackets();
	}
	
	private void executeNextEvent(String key, double leastEventTime) {
		
		String eventID = Packets.get(key).getNextEvent().getEventID();
		if(eventID.equals("wait")){
			Packets.get(key).getNextEvent().excuteEvent(leastEventTime);
			Packets.get(key).addToEvents();
		}
		else if(Packets.get(key).getNextEvent().getTimeTaken()==leastEventTime){
			Packets.get(key).getNextEvent().excuteEvent(leastEventTime);
			Packets.get(key).addToEvents();
		}
		else{
			Packets.get(key).getNextEvent().halfExecuteEvent(leastEventTime);
		}
		
		
	}
	private void processPacketNextEvent(String packetName) {
		String currentLocationType = Packets.get(packetName).getCurrentLocationType();
		String currentLocation = Packets.get(packetName).getCurrentLocation();
		
		if(currentLocationType.equals("InputQ")){
			
			
		//	System.out.println(Packets.get(packetName).getID()+" is in inputQ "+currentLocation);
			int routerID = Integer.parseInt(currentLocation.split(" to ")[1]);
			if(InputBuffer.get(currentLocation).packetIsAtExit(packetName.toString())){
				int nextRouter=this.forwardingTable[routerID][Packets.get(packetName).getDest()];
				if(nextRouter==-1){
					Simulator.Packets.get(packetName).markAsLost();
					Simulator.InputBuffer.get(currentLocation).removePacket();
					System.out.println(packetName+" reached destination");
					
				}
				
				
				else if(checkOutputQ(nextRouter,routerID,Packets.get(packetName).getSize())){
					NextEvent newEvent = new ProcessSwitch("process&switch",Routers.get(currentLocation.split(" to ")[1]).getProcessingDelay(),currentLocation,routerID+" to "+nextRouter,packetName);
					Packets.get(packetName).setNextEvent(newEvent);
				}
				else{
					NextEvent newEvent = new Wait("wait",Double.MAX_VALUE,packetName,currentLocation);
					Packets.get(packetName).setNextEvent(newEvent);
				}
				
			}
			else{
				NextEvent newEvent = new Wait("wait",Double.MAX_VALUE,packetName,currentLocation);
				Packets.get(packetName).setNextEvent(newEvent);
			}
			
		}
		else if(currentLocationType.equals("OutputQ")){
			
	//		System.out.println(Packets.get(packetName).getID()+" is in outputQ "+currentLocation);
			if(OutputBuffer.get(currentLocation).packetIsAtExit(packetName.toString())){
				if(Links.get(currentLocation).linkIsClear()){
					//System.out.println("time for transmission: "+Routers.get(currentLocation.split(" to ")[0]).getTransmittingDelay(Packets.get(packetName).getSize()));
					NextEvent newEvent = new TransmitToLink("transmitToLink",Routers.get(currentLocation.split(" to ")[0]).getTransmittingDelay(Packets.get(packetName).getSize()),currentLocation,currentLocation,packetName);
					Packets.get(packetName).setNextEvent(newEvent);
				}
				else{
					NextEvent newEvent = new Wait("wait",Double.MAX_VALUE,packetName,currentLocation);
					Packets.get(packetName).setNextEvent(newEvent);
				}
				
				
			}
			else{
				Wait newEvent = new Wait("wait",Double.MAX_VALUE,packetName,currentLocation);
				Packets.get(packetName).setNextEvent(newEvent);
			}
			
		}
		else if(currentLocationType.equals("onLinkEdge")){
			
	//		System.out.println(Packets.get(packetName).getID()+" is on edge of link "+currentLocation);
			if(checkInputQ(currentLocation,Packets.get(packetName).getSize())){
				NextEvent newEvent = new TransmitFromLink("transmitFromLink",Routers.get(currentLocation.split(" to ")[1]).getTransmittingDelay(Packets.get(packetName).getSize()),currentLocation,currentLocation,packetName);
				Packets.get(packetName).setNextEvent(newEvent);
			}
			else{
				NextEvent newEvent = new Lose("lose",Double.MAX_VALUE,packetName);
				Packets.get(packetName).setNextEvent(newEvent);
			}
		}
		else if(currentLocationType.equals("transmittedToLink")){
			
	//		System.out.println(Packets.get(packetName).getID()+" is at the beginnig of link "+currentLocation);
			NextEvent newEvent = new Propagating("propagating",Links.get(currentLocation).getPropagatingDelay(),packetName,currentLocation);
			Packets.get(packetName).setNextEvent(newEvent);
		}
		else{
	//		System.out.println("ftw");
		}
	}
	private boolean checkInputQ(String currentLocation, double packetSize) {
		return InputBuffer.get(currentLocation).addPacketVirtually(packetSize);
	}
	private boolean checkOutputQ(int nextRouter, int routerID, double packetSize) {
		return OutputBuffer.get(routerID+" to "+nextRouter).addPacketVirtually(packetSize);
	}
	private void setUpForwadingTable() {
		boolean firstLine=true;
		String network="C.txt";
		//String matFile = "C:/Hiruna/CO227/Project/src/"+network;
		String matFile = "./src/main/java/"+network;
		String line = "";
		try (BufferedReader br = new BufferedReader(new FileReader(matFile))) {
        	while ((line = br.readLine()) != null ) {
        		String [] cmd = line.split(" ");
        		if(firstLine){
        			noRouters=Integer.valueOf(cmd[0]);
        			noLinks=Integer.valueOf(cmd[1]);
        			adjecencyMat = new int[noRouters][noRouters];
        			firstLine=false;
        			for(int i=0;i<noRouters;i++){
        				Queue tempQ1 = new Queue(i+" to "+i,"InputQ",""+i,10D);
        				InputBuffer.put(i+" to "+i,tempQ1);
        			}
        		}
        		else{
        			
        			int router1=Integer.valueOf(cmd[0]);
        			int router2=Integer.valueOf(cmd[1]);
        			double linkDistance = Double.parseDouble(cmd[2]);
        			double linkSpeed = Double.parseDouble(cmd[3]);
        			
        			Link tempLink1 = new Link((router1)+" to "+(router2),"onLink",linkDistance,linkSpeed);
        			Links.put((router1)+" to "+(router2), tempLink1);
        			Link tempLink2 = new Link((router2)+" to "+(router1),"onLink",linkDistance,linkSpeed);
        			Links.put((router2)+" to "+(router1), tempLink2);
        			
        			
        			Queue tempQ1 = new Queue((router1)+" to "+(router2),"InputQ",""+(router2),linkSpeed);
        			Queue tempQ2 = new Queue((router2)+" to "+(router1),"InputQ",""+(router1),linkSpeed);
        			Queue tempQ3 = new Queue((router1)+" to "+(router2),"OutputQ",""+(router1),linkSpeed);
        			Queue tempQ4 = new Queue((router2)+" to "+(router1),"OutputQ",""+(router2),linkSpeed);
        			
        			
        			InputBuffer.put((router1)+" to "+(router2), tempQ1);
        			InputBuffer.put((router2)+" to "+(router1), tempQ2);
        			OutputBuffer.put((router1)+" to "+(router2), tempQ3);
        			OutputBuffer.put((router2)+" to "+(router1), tempQ4);
        			
        			adjecencyMat[router1][router2]=1;
        			adjecencyMat[router2][router1]=1;
        		}     		
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
//		for(int i=0;i<adjecencyMat.length;i++){
//			for(int j=0;j<adjecencyMat.length;j++){
//				System.out.print(adjecencyMat[i][j]+" ");
//			}
//			System.out.println();
//		}

		int src=0;
		forwardingTable= new int[noRouters][noRouters];
		ForwadingTable table1= new ForwadingTable(adjecencyMat,src,forwardingTable);
		
		for(int i=0;i<noRouters;i++){
			Router tempRouter = new Router(i,2D,4D);
			String tempKey = i+"";
			Routers.put(tempKey, tempRouter);
		}
	}
	
}
