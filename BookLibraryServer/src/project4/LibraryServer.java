/* LibraryServer receives LibraryClients 
 * and allows clients to view, rent, and return
 * books from the library.
 * Designed by Jason Rimer, December 15, 2013.
 */
package project4;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.*;

public class LibraryServer implements LibraryConstants{
	//field variables shared across inner classes and methods
	private LibraryDatabase library;
	private JTextArea jtaServerInfo;
	private ServerSocket serverSocket;
	private JTextField jtfFilePath;
	//main
	public static void main(String[] args) throws Exception{
		//instantiate new LibraryServer object which executes the rest of the program
		new LibraryServer();
	}
	//LibraryServer constructor that triggers other inner classes and LibraryDatabase
	public LibraryServer() throws Exception{
		JPanel panel = new JPanel();
		JFrame frame = new JFrame();
		jtfFilePath = new JTextField("Enter the .txt library file location here then press start...");
		jtaServerInfo = new JTextArea("Server information is displayed in this text area.\n");
//		//use while testing to instantly start program with file path
//		jtfFilePath.setText("/Users/Atlas/Documents/Eclipse/workspace/Project4Server/src/project4/LibraryDatabase");
//		library = new LibraryDatabase(jtfFilePath.getText());
		//Start button
		JButton jbStart = new JButton("Start");
		jbStart.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try{
				if (!new File(jtfFilePath.getText()).exists()) throw new FileNotFoundException();
					else{
						library = new LibraryDatabase(jtfFilePath.getText());
						jtaServerInfo.append("Library successfully created.\n	Now accepting clients...\n");
					}
				} catch(FileNotFoundException e){
					jtaServerInfo.append("File not found. Check you path for the Library Text File and try again.\n");
				}
				
			}
		});
		//inner panel
		panel.setLayout(new BorderLayout());
		panel.add(jtfFilePath, BorderLayout.NORTH);
		panel.add(new JScrollPane(jtaServerInfo), BorderLayout.CENTER);
		panel.add(jbStart, BorderLayout.SOUTH);
		//frame
		frame.setSize(600,400);
		frame.add(panel);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		//server block
		try{
			//create a server socket
			serverSocket = new ServerSocket(8000);
			jtaServerInfo.append("Server started at " + new Date() + ".\n	Add the library above.\n");
			//thread pool to handle multiple clients
			ExecutorService executor = Executors.newCachedThreadPool();
			//receive multiples clients and execute threads therein
			while(true){
				Socket socket = serverSocket.accept();
				jtaServerInfo.append("Client started at " + new Date() + "\n");
				executor.execute(new HandleClient(socket));
			}
		} catch (IOException e){
			System.out.println(e);
		}
	}
	//Handles all clients by listening for actions and implementing client requests
	class HandleClient implements Runnable{
		private Socket socket;
		//constructor
		HandleClient(Socket socket) throws IOException, EOFException{
			this.socket = socket;
		}
		//handle all input/output from/to client
		@Override
		public void run() {						
			try {
				//initialize variables
				int intFromClient = 0;
				int bookIndex = 0;
				int checkedOut = 0;
				int result = 0;
				//create streams
				DataInputStream inputFromClient = new DataInputStream(socket.getInputStream());
				DataOutputStream outputToClient = new DataOutputStream(socket.getOutputStream());
				ObjectOutputStream objectToClient = new ObjectOutputStream(socket.getOutputStream()); 
				//Send library to start client
				objectToClient.writeObject(library);
				//continuously serve client
				while (true){
					//get desired action from client
					intFromClient = inputFromClient.readInt();
					//get index of book being acted upon
					bookIndex = inputFromClient.readInt();
					//start rent sequence if client wants to rent a book
					if (intFromClient == RENT){
						//maximum rental amount
						if (checkedOut < 3){
							jtaServerInfo.append("Attempting to rent ");
							//loop through books to find equal index
							for(Integer i : library.getBookMap().keySet()){
								//same book
								if (library.getBookMap().get(i).getIndexOfBook() == bookIndex){
									jtaServerInfo.append(library.getBookMap().get(i).getTitle() + "...\n");
									//Attempt to acquire lock, wait if unable
									if (!library.getBookMap().get(i).getLock().tryLock()){
										jtaServerInfo.append("Waiting for " + library.getBookMap().get(i).getTitle() + "\n");
										outputToClient.writeInt(WAITING);
										while (!library.getBookMap().get(i).getLock().tryLock() && intFromClient != CANCEL){
											result = WAITING;	
											outputToClient.writeInt(WAITING);
											intFromClient = inputFromClient.readInt();
										}
										//Client cancels request
										if (intFromClient == CANCEL){
											result = CANCEL;
											outputToClient.writeInt(CANCEL);
											jtaServerInfo.append("Cancelled " + library.getBookMap().get(i).getTitle() + " request.\n");
										}
										//Wait is successful and not cancelled
										else {
											//Attempt rental
											result = library.getBookMap().get(i).rentOrReturnBook(RENT);
											if (result == RENT_SUCCESS) {
												outputToClient.writeInt(RENT_SUCCESS);
												checkedOut++;
												jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " was rented.\n");
											}
											else if (result == RENT_ERROR){
												outputToClient.writeInt(RENT_ERROR);
												jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " is out of stock - not rented.\n");
											}
											library.getBookMap().get(i).getLock().unlock();
										}
									}
									//Immediately receive lock
									else {
										result = library.getBookMap().get(i).rentOrReturnBook(RENT);
										if (result == RENT_SUCCESS) {
											outputToClient.writeInt(RENT_SUCCESS);
											checkedOut++;
											jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " was rented.\n");
										}
										else if (result == RENT_ERROR){
											outputToClient.writeInt(RENT_ERROR);
											jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " is out of stock - not rented.\n");
										}
										library.getBookMap().get(i).getLock().unlock();
									}
								}
							}
						}
						//at max checkout limit
						else {
							outputToClient.writeInt(RENT_LIMIT);
							jtaServerInfo.append("Client rent limit reached. No rental.\n");
						}
					}
					//Start return sequence if client wants to rent a book
					else if (intFromClient == RETURN){
						//Client has enough books to return
						if (checkedOut > 0){
							jtaServerInfo.append("Attempting to return ");
							//Loop through books to find equal index
							for(Integer i : library.getBookMap().keySet()){
								//Same book
								if (library.getBookMap().get(i).getIndexOfBook() == bookIndex){
									jtaServerInfo.append(library.getBookMap().get(i).getTitle() + "...\n");
									//Attempt to acquire lock, wait if unable
									if (!library.getBookMap().get(i).getLock().tryLock()){
										jtaServerInfo.append("Waiting for " + library.getBookMap().get(i).getTitle() + "\n");
										outputToClient.writeInt(WAITING);
										while (!library.getBookMap().get(i).getLock().tryLock() && intFromClient != CANCEL){
											result = WAITING;	
											outputToClient.writeInt(WAITING);
											intFromClient = inputFromClient.readInt();	
										}
										//Client cancels request
										if (intFromClient == CANCEL){
											result = CANCEL;
											outputToClient.writeInt(CANCEL);
											jtaServerInfo.append("Cancelled " + library.getBookMap().get(i).getTitle() + "\n");
										}
										//Wait is successful and request not cancelled
										else {
											result = library.getBookMap().get(i).rentOrReturnBook(RETURN);
											if (result == RETURN_SUCCESS) {
												outputToClient.writeInt(RETURN_SUCCESS);
												jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " was returned.\n");
											}
											else if (result == RETURN_ERROR){
												outputToClient.writeInt(RETURN_ERROR);
												checkedOut--;
												jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " stock is full - not returned.\n");
											}
											library.getBookMap().get(i).getLock().unlock();
										}
									}
									else {
										result = library.getBookMap().get(i).rentOrReturnBook(RETURN);
										if (result == RETURN_SUCCESS) {
											outputToClient.writeInt(RETURN_SUCCESS);
											checkedOut--;
											jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " was returned.\n");
										}
										else if (result == RETURN_ERROR){
											outputToClient.writeInt(RETURN_ERROR);
											jtaServerInfo.append(library.getBookMap().get(i).getTitle() + " stock is full - not returned.\n");
										}
										library.getBookMap().get(i).getLock().unlock();
									}
								}
							}
						}
						//Client has no books to return
						else {
							outputToClient.writeInt(RETURN_LIMIT);
							jtaServerInfo.append("Client has no books to return. No return.\n");
						}
					}
				}
			}
			//When a client exits it throws the EOFException because the pipe breaks; catch and close socket to prevent error
			catch(EOFException e1){
				try {
					System.out.println("Server closing socket.");
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			catch(IOException e){
				System.err.println("Server failed: " + e);
				e.printStackTrace();
			}	
		}
	}
}
