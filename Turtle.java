package main;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import org.python.core.*;
import org.python.util.*;
import javax.swing.*;

/**
 * A better replacement for the turtle library
 * 
 * @author EPICI
 * @version 1.0
 */
public class Turtle extends PyObject {
	private static final long serialVersionUID = 1L;
	
	private static long random = System.nanoTime()|1;
	private static long time = System.currentTimeMillis();
	private static int dimrx,dimry;
	private static double scale;
	private static BufferedImage canvas,forward;
	private static Graphics2D graphics,fwgraphics;
	private static HashMap<Integer,Turtle> turtles;
	private static Stack<PyObject> events;
	private static Stack[] listeners;
	private static long delay;
	
	private static JFrame frame;
	
	private int id;
	private double[] contourx = {0.02d,-0.02d,-0.01d,-0.02d};
	private double[] contoury = {0d   , 0.02d, 0d,   -0.02d};
	
	/**
	 * The properties
	 */
	public PyObject xpos,ypos,heading,width,show,pen,color;
	
	/**
	 * The default constructor
	 * <br>
	 * Instantiates and registers a turtle
	 */
	public Turtle(){
		id = (int)((random=random*0xaddf7)>>32);
		xpos = ypos = heading = new PyFloat(0d);
		width = new PyFloat(0.01d);
		show = pen = new PyBoolean(true);
		color = new PyTuple(new PyFloat(0d),new PyFloat(0d),new PyFloat(0d));
		turtles.put(id, this);
	}
	
	/**
	 * Add the turtle back to the list
	 */
	public void register(){
		turtles.put(id, this);
	}
	
	/**
	 * Makes the turtle stop drawing
	 */
	public void unregister(){
		turtles.remove(id);
	}
	
	private void setShape(PyObject shape){
		try{
			int n = shape.__len__();
			double[] bufx = new double[n];
			double[] bufy = new double[n];
			for(int i=0;i<n;i++){
				PyObject coords = shape.__getitem__(i);
				bufx[i] = coords.__getitem__(0).asDouble();
				bufy[i] = coords.__getitem__(1).asDouble();
			}
			contourx = bufx;
			contoury = bufy;
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Set any or all properties
	 * 
	 * @param args *args
	 * @param keywords **kwargs
	 */
	public void set(PyObject[] args,String[] keywords){
		int i=0,j=args.length-keywords.length;
		if(j!=0)throw new IllegalArgumentException("Too many arguments\nYou're supposed to use keywords");
		for(;i<keywords.length;i++,j++){
			String key = keywords[i];
			PyObject value = args[j];
			if(value==null || value.getType().equals(PyNone.TYPE))continue;
			switch(key.toLowerCase()){
			case "x":{
				xpos = value;
				break;
			}
			case "y":{
				ypos = value;
				break;
			}
			case "xy":
			case "pos":
			case "position":{
				PyObject iter = value.__iter__();
				xpos = iter.__iternext__();
				ypos = iter.__iternext__();
				break;
			}
			case "h":
			case "heading":{
				heading = value;
				break;
			}
			case "t":
			case "thickness":
			case "w":
			case "width":{
				width = value;
				break;
			}
			case "p":
			case "pen":
			case "penupdown":
			case "penstate":{
				pen = value;
			}
			case "d":
			case "draw":
			case "v":
			case "visible":
			case "visibility":
			case "s":
			case "show":{
				show = value;
				break;
			}
			case "c":
			case "color":
			case "colour":{
				color = value;
				break;
			}
			case "shape":{
				setShape(value);
				break;
			}
			}
		}
	}
	
	/**
	 * Moves the turtle forward by some amount, negative value will move backwards
	 * 
	 * @param distance the distance forward to move
	 */
	public void forward(PyObject distance){
		double mag = distance.asDouble();
		if(mag==0)return;
		double px = xpos.asDouble(), py = ypos.asDouble(), pr = Math.toRadians(heading.asDouble());
		double dx = mag*Math.cos(pr), dy = mag*Math.sin(pr);
		double nx = px+dx, ny = py+dy;
		if(bool(pen)){
			preDraw(graphics);
			line(px,py,nx,ny,width.asDouble());
		}
		xpos = new PyFloat(nx);
		ypos = new PyFloat(ny);
	}
	
	/**
	 * Turn left by a certain amount
	 * 
	 * @param degrees the amount to turn in degrees
	 */
	public void left(PyObject degrees){
		heading = new PyFloat(heading.asDouble()+degrees.asDouble());
	}
	
	/**
	 * Turn right by a certain amount
	 * 
	 * @param degrees the amount to turn in degrees
	 */
	public void right(PyObject degrees){
		heading = new PyFloat(heading.asDouble()-degrees.asDouble());
	}
	
	/**
	 * Draw a circle around the turtle's current position
	 * 
	 * @param args *args
	 * @param keywords **kwargs
	 */
	public void circle(PyObject radius){
		if(!bool(pen))return;
		double pr = radius.asDouble();
		int r = (int)(pr*scale),x = dimrx+(int)(xpos.asDouble()*scale),y = dimry-(int)(ypos.asDouble()*scale);
		int thickness = (int)(scale*width.asDouble());
		if(thickness<=0)thickness = 1;
		preDraw(graphics);
		graphics.setStroke(new BasicStroke(thickness));
		graphics.drawOval(x-r, y-r, r<<1, r<<1);
	}
	
	/**
	 * Stamp to the main canvas
	 * <br>
	 * Ignores drawing restrictions
	 */
	public void stamp(){
		stampTo(graphics);
	}
	
	private void stampTo(Graphics2D target){
		int n = contourx.length;
		double px = xpos.asDouble(), py = ypos.asDouble(), pr = Math.toRadians(heading.asDouble());
		double sin = Math.sin(pr), cos = Math.cos(pr);
		int[] xs = new int[n], ys = new int[n];
		for(int i=0;i<n;i++){
			double ox = contourx[i], oy = contoury[i];
			xs[i] = dimrx+(int)((px+ox*cos-oy*sin)*scale);
			ys[i] = dimry-(int)((py+oy*cos+ox*sin)*scale);
		}
		preDraw(target);
		target.fillPolygon(xs, ys, n);
	}
	
	private static boolean bool(PyObject object){
		return object.asInt()!=0;
	}
	
	private void preDraw(Graphics2D target){
		PyObject iter = color.__iter__();
		double pr = iter.__iternext__().asDouble();
		double pg = iter.__iternext__().asDouble();
		double pb = iter.__iternext__().asDouble();
		int r = (int)Math.round(pr*255), g = (int)Math.round(pg*255), b = (int)Math.round(pb*255);
		target.setColor(new Color(r,g,b));
	}
	
	private static void line(double x1,double y1,double x2,double y2,double thickness){
		line((int)(x1*scale),(int)(y1*scale),(int)(x2*scale),(int)(y2*scale),(int)(thickness*scale));
	}
	
	private static void line(int x1,int y1,int x2,int y2,int thickness){
		if(thickness<=0)thickness = 1;
		graphics.setStroke(new BasicStroke(thickness));
		graphics.drawLine(dimrx+x1, dimry-y1, dimrx+x2, dimry-y2);
	}
	
	private static void setCanvas(BufferedImage toSet){
		canvas = toSet;
		graphics = (Graphics2D) canvas.getGraphics();
		forward = new BufferedImage(canvas.getWidth(),canvas.getHeight(),BufferedImage.TYPE_INT_ARGB);
		fwgraphics = (Graphics2D) forward.getGraphics();
	}
	
	private static void push(){
		forward.setData(canvas.getRaster());
	}
	
	private static void onKey(int id){
		Stack<PyObject> list = listeners[id];
		for(PyObject obj:list)
			events.push(obj);
	}
	
	/**
	 * Bind a listener to a key
	 * 
	 * @param args *args
	 * @param keywords **kwargs
	 */
	public static void listen(PyObject[] args,String[] keywords){
		if(args.length!=2 || keywords.length!=0)throw new IllegalArgumentException("Incorrect number of arguments\nFirst argument should be key, second argument should be function to call");
		listeners[keyFor(args[0].asString())].push(args[1]);
	}
	
	private static int keyFor(String s){
		switch(s.toLowerCase()){
		case "enter":
		case "return":return KeyEvent.VK_ENTER;
		case "esc":
		case "escape":return KeyEvent.VK_ESCAPE;
		case "left":return KeyEvent.VK_LEFT;
		case "right":return KeyEvent.VK_RIGHT;
		case "up":return KeyEvent.VK_UP;
		case "down":return KeyEvent.VK_DOWN;
		case "shift":return KeyEvent.VK_SHIFT;
		case "ctrl":
		case "control":return KeyEvent.VK_CONTROL;
		}
		return s.charAt(0);
	}
	
	private static long time(){
		long newTime = System.currentTimeMillis();
		long diff = newTime - time;
		time = newTime;
		return diff;
	}
	
	private static void delay(long time){
		try{
			Thread.sleep(time);
		}catch(InterruptedException e){
			e.printStackTrace();
		}
	}
	
	public static PyObject loop(){
		return new PyIterator(){
			@Override
			public PyObject __iter__(){
				return this;
			}
			
			@Override
			public PyObject __iternext__(){
				push();
				Stack<PyObject> oevents = events;
				events = new Stack<PyObject>();
				for(PyObject object:oevents)
					object.__call__();
				for(Turtle turtle:turtles.values()){
					if(bool(turtle.show))
						turtle.stampTo(fwgraphics);
				}
				frame.repaint();
				long rem = delay - time();
				if(rem>0)delay(rem);
				return this;
			}
		};
	}
	
	/**
	 * The main method, not to be called
	 * 
	 * @param args ignored
	 */
	public static void main(String[] args) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("init.cfg")));
		int width = Integer.parseInt(br.readLine());
		int height = Integer.parseInt(br.readLine());
		delay = Integer.parseInt(br.readLine());
		String scriptName = br.readLine();
		br.close();
		dimrx = width>>1;
		dimry = height>>1;
		scale = 0.5d*Math.hypot(width, height);
		setCanvas(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB));
		turtles = new HashMap<>();
		events = new Stack<>();
		listeners = new Stack[128];
		for(int i=0;i<128;i++)
			listeners[i] = new Stack<PyObject>();
		frame = new JFrame("Turtles");
		frame.add(new JLabel(new ImageIcon(forward)));
		frame.addKeyListener(new KeyListener(){

			@Override
			public void keyTyped(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void keyPressed(KeyEvent e) {
				onKey(e.getKeyCode());
			}

			@Override
			public void keyReleased(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}
			
		});
		frame.requestFocusInWindow();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
		frame.setResizable(false);
		PythonInterpreter interpreter = new PythonInterpreter();
		interpreter.exec("from main import Turtle");
		interpreter.execfile(scriptName);
		interpreter.close();
	}
}
