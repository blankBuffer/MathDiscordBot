package mathbot;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ui.UI;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;

import cas.*;
import cas.graphics.ExprRender;
import cas.lang.Ask;
import cas.lang.Interpreter;
import cas.primitive.*;

public class Main {
	static HashMap<String,CasInfo> userDefinitions = null;
	static String token = null;
	static JDA jda = null;
	static Color RESULT_COLOR = Color.white;
	static int FONT_RESOLUTION = 48;
	
	public static void main(String[] args){
		System.out.println(UI.CRED);
		cas.Rule.loadRules();
		
		login(args);
		try
		{
			jda = JDABuilder.createDefault(token).build();
			jda.awaitReady();
			loadUserCasInfo();
			jda.getPresence().setActivity(Activity.playing("with numbers"));
			jda.addEventListener(responder);
			
		}catch (LoginException e){
			e.printStackTrace();
			System.exit(-1);
		}catch (InterruptedException e){
			e.printStackTrace();
			System.exit(-1);
		}
		autoSaveThread.start();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
		}
		
	}
	
	
	
	static void end() {
		saveUserCasInfo();
		logger.log("stopping");
		try {
			autoSaveThread.interrupt();
			autoSaveThread.join();
			logger.log.close();
			jda.shutdown();
		} catch (Exception e) {
			System.err.println("error while closing");
		}
		
	}
	
	static Logger logger = new Logger();
	static class Logger{
		FileWriter log = null;
		
		Logger(){
			try {
				log = new FileWriter("discordMathBot.log",true);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
		public void log(String text) {
			System.out.println(text);
			try {
				log.write(text+"\n");
				log.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	static Thread autoSaveThread = new Thread("auto-save") {
		@Override
		public void run() {
			logger.log("starting autosave thread!");
			while(true) {
				try {
					Thread.sleep(1000*60*20);
					saveUserCasInfo();
				} catch (InterruptedException e) {
					saveUserCasInfo();
					logger.log("stopping autosave thread");
					break;
				}
			}
		}
	};
	
	final static String loginFileName = "login.txt";
	public static void login(String[] args){
		if(args.length != 1) {
			System.out.println("no parameters, opening "+loginFileName);
			try {
				Scanner s = new Scanner(new File(loginFileName));
				token = s.next();
				s.close();
			} catch (FileNotFoundException e1) {
				System.out.println("no login file");
				System.exit(-1);
			}
		}else {
			token = args[0];
		}
	}
	
	static ListenerAdapter responder = new ListenerAdapter() {
		@Override
		public void onMessageReceived(MessageReceivedEvent event){
			
			long oldTime = System.nanoTime();
			
			if(event.getAuthor().isBot()) return;
			
			String  request = event.getMessage().getContentRaw();
			
			MessageChannel channel = event.getChannel();
			
			String start = null;
			if(request.length()>3) {
				start = request.substring(0, 3).toLowerCase();
			}else {
				return;
			}
			
			if(start.equals("bot") ) {
				
				String user = event.getAuthor().toString();
				if(!userDefinitions.containsKey(user)) {
					logger.log("created user definitions for "+user+"!");
					userDefinitions.put(user, new CasInfo());
				}
				
				logger.log("---------------------------------------");
				
				CasInfo casInfo = userDefinitions.get(user);
				
				logger.log(user);
				logger.log("request:"+request);
				
				if(request.contains("STOP")) {
					end();
					return;
				}
				
				Expr assumedInput = null;
				Expr response = null;
				try {
					assumedInput = Ask.ask(request);
					response = assumedInput.simplify(casInfo);
					logger.log("interpreting as:"+assumedInput);
					logger.log("response:"+response);
				}catch(Exception e) {
					channel.sendMessage("`error! "+e.getMessage()+"`").queue();
					logger.log(e.getMessage());
					e.printStackTrace();
					return;
				}
				logger.log("interpreting as:"+assumedInput);
				logger.log("response:"+response);
				
				if(request.contains("DEBUG")) channel.sendMessage("`interpreting as: "+assumedInput+"\nanswer: "+response+"`").queue();
				
				if(response instanceof ObjectExpr) {
					ObjectExpr casted = (ObjectExpr)response;
					File f = new File("graph.jpeg");
					try {
						ImageIO.write((BufferedImage)casted.object, "jpeg", f);
					} catch (IOException e) {
						e.printStackTrace();
					}
					channel.sendFile(f).queue();
				}else {
					File f = new File("eq.png");
					try {
						channel.sendMessage("```"+response.toString()+"```").queue();
						if(!(response instanceof Var)) {
							if( Interpreter.isProbablyExpr(request) ) {
								ImageIO.write(ExprRender.createImg(QuickMath.becomes(assumedInput,response), RESULT_COLOR ,FONT_RESOLUTION), "png", f);
								channel.sendFile(f).queue();
							}else {
								ImageIO.write(ExprRender.createImg(response, RESULT_COLOR ,FONT_RESOLUTION), "png", f);
								channel.sendFile(f).queue();
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				long delta = System.nanoTime()-oldTime;
				logger.log("took "+delta/1000000.0+" ms to complete");
			}
			
		}
	};
	
	@SuppressWarnings("unchecked")
	static void loadUserCasInfo() {
		try {
			FileInputStream fin = new FileInputStream("userData.dat");
			ObjectInputStream ois = new ObjectInputStream(fin);
			userDefinitions = (HashMap<String, CasInfo>) ois.readObject();
			ois.close();
			logger.log("loaded user data!");
			return;
		} catch (FileNotFoundException e) {
			logger.log("no user data file!");
		} catch (IOException e) {
			logger.log("issue while loading user data!");
		} catch (ClassNotFoundException e) {
			logger.log("issue while loading user data!");
		}
		userDefinitions = new HashMap<String,CasInfo>();
		logger.log("creating new user data file!");
		saveUserCasInfo();
	}
	
	static void saveUserCasInfo() {
		try {
			FileOutputStream fos = new FileOutputStream("userData.dat");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(userDefinitions);
			fos.close();
			logger.log("saved user data!");
		} catch (FileNotFoundException e) {
			logger.log("issue while saving user data!");
		} catch (IOException e) {
			logger.log("issue while saving user data!");
		}
	}
	
}
