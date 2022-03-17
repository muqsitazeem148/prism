package strat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;


import parser.State;
import prism.PrismException;
import prism.PrismLog;
import explicit.Distribution;
import explicit.MDPExplicit;
import explicit.MDPSimple;
import explicit.MDPSparse;
import explicit.Model;
import explicit.NondetModel;
import explicit.STPG;

@XmlRootElement
public class MultiLongRunStrategy implements Strategy, Serializable
{
	public static final long serialVersionUID = 0L;
	
	// strategy info
	protected String info = "No information available.";

	// storing last state
	protected transient int lastState;

	@XmlElementWrapper(name = "transientChoices")
	@XmlElement(name = "distribution")
	protected Distribution[] transChoices;
	
	@XmlElementWrapper(name = "reccurentChoices")
	@XmlElement(name = "distribution")
	protected Distribution[] recChoices;

	@XmlElementWrapper(name = "switchingProbabilities")
	protected double[] switchProb;
	private transient boolean isTrans; //represents the single bit of memory
	
	private MultiLongRunStrategy() {
		//for XML serialization by JAXB
	}
	
	/**
	 * Loads a strategy from a XML file
	 * @param filename
	 */
	public static MultiLongRunStrategy loadFromFile(String filename) {
		try {
			File file = new File(filename);
			//InputStream inputStream = new FileInputStream(file);
			JAXBContext jc = JAXBContext.newInstance(MultiLongRunStrategy.class);
			Unmarshaller u = jc.createUnmarshaller();
			return (MultiLongRunStrategy) u.unmarshal(file);
		} catch (JAXBException ex) {
			ex.printStackTrace(); //TODO something more clever
			return null;
		}
	}
	/**
	 * 
	 * Creates a multi-long run strategy
	 *
	 * @param minStrat minimising strategy
	 * @param minValues expected values for states for min strategy
	 * @param maxStrat maximising strategy
	 * @param maxValues expected value for states for max strategy
	 * @param targetValue value to be achieved by the strategy
	 * @param model the model to provide info about players and transitions
	 */
	public MultiLongRunStrategy(Distribution[] transChoices, double[] switchProb, Distribution[] recChoices)
	{
		this.transChoices = transChoices;
		this.switchProb = switchProb;
		this.recChoices = recChoices;
	}
	
	/**
	 * 
	 * Creates a multi-long run strategy which switches memory elements
	 * as soon as recurrent distr is defined
	 *
	 * @param minStrat minimising strategy
	 * @param minValues expected values for states for min strategy
	 * @param maxStrat maximising strategy
	 * @param maxValues expected value for states for max strategy
	 * @param targetValue value to be achieved by the strategy
	 * @param model the model to provide info about players and transitions
	 */
	public MultiLongRunStrategy(Distribution[] transChoices, Distribution[] recChoices)
	{
		this.switchProb = null;
		this.transChoices = transChoices;
		this.recChoices = recChoices;
	}

	/**
	 * Creates a ExactValueStrategy.
	 *
	 * @param scan
	 */
	public MultiLongRunStrategy(Scanner scan)
	{
	}

	private boolean switchToRec(int state)
	{
		if (this.switchProb == null)
			return this.recChoices[state] != null;
		else
			return Math.random() < this.switchProb[state];
	}
	

	public void init(int state) throws InvalidStrategyStateException
	{
		isTrans = !switchToRec(state);
		System.out.println("init to " + isTrans);
		lastState = state;
	}


	public void updateMemory(int action, int state) throws InvalidStrategyStateException
	{
		if (isTrans) {
			isTrans = !switchToRec(state);
		}
		lastState = state;
	}


	public Distribution getNextMove(int state) throws InvalidStrategyStateException
	{
		return (isTrans) ? this.transChoices[state] : this.recChoices[state];
	}


	public void reset()
	{
		lastState = -1;
	}


	public void exportToFile(String file)
	{
		try {
			JAXBContext context = JAXBContext.newInstance(MultiLongRunStrategy.class);
			
			Marshaller m = context.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			FileWriter out = new FileWriter(new File(file));			
			m.marshal(this, out);
			out.close();
		} catch (JAXBException ex) { //TODO do something more clever
			ex.printStackTrace(); 
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		/*try {
			out = new FileWriter(new File(file));
			out.write("transient: " + Arrays.toString(transChoices) + "\n");
			out.write("reccurent: " + Arrays.toString(recChoices) + "\n");
			out.write("switching probs: " + Arrays.toString(switchProb) + "\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
					// do nothing
				}
		}*/
	}


	public Model buildProduct(Model model) throws PrismException
	{
		return buildProductFromMDPExplicit((MDPSparse) model);
	}
	
	public Model buildProductFromMDPExplicit(MDPSparse model) throws PrismException
	{
		
		// construct a new STPG of size three times the original model
		MDPSimple mdp = new MDPSimple(3 * model.getNumStates());
		
		int n = mdp.getNumStates();

		List<State> oldStates = model.getStatesList();

		// creating helper states
		State stateInit = new State(1), stateTran = new State(1), stateRec = new State(1);
		stateInit.setValue(0, 0); // state where memory is not yet initialised
		stateTran.setValue(0, 1); // state where target is minimum elem
		stateRec.setValue(0, 2); // state where target is maximum element

		// creating product state list
		List<State> newStates = new ArrayList<State>(n);
		for (int i = 0; i < oldStates.size(); i++) {
			newStates.add(new State(oldStates.get(i), stateInit));
			newStates.add(new State(oldStates.get(i), stateTran));
			newStates.add(new State(oldStates.get(i), stateRec));
		}

		// setting the states list to STPG
		mdp.setStatesList(newStates);

		// adding choices for the product STPG
		// initial distributions
		int indx;
		Distribution distr;
		
		distr = new Distribution();
		if (this.switchProb[0] != 1)
			distr.add(1, 1 - this.switchProb[0]);
		if (this.switchProb[0] != 0)
			distr.add(2, this.switchProb[0]);
		mdp.addChoice(0, distr);
		
		for (int i = 1; i < oldStates.size(); i++) {
			indx = 3 * i;

			//Add self-loop only
			distr = new Distribution();
			distr.add(indx, 1.0);
			mdp.addChoice(indx, distr);
			
		}

		// all other states
		for (int i = 0; i < oldStates.size(); i++) {
			int tranIndx = 3 * i + 1;
			int recIndx = 3 * i + 2;

			Distribution distrTranState = new Distribution();
			Distribution distrRecState = new Distribution();
			
			Distribution choicesTran = this.transChoices[i];
			Distribution choicesRec = this.recChoices[i];

			//recurrent states
			if (choicesRec != null) { //MEC state
				for (Entry<Integer, Double> choiceEntry : choicesRec) {
					Iterator<Entry<Integer, Double>> iterator = model.getTransitionsIterator(i, choiceEntry.getKey());
					while(iterator.hasNext()) {
						Entry<Integer,Double> transitionEntry = iterator.next();
						distrRecState.add(transitionEntry.getKey(), choiceEntry.getValue() * transitionEntry.getValue());
					}
				}
				
				mdp.addChoice(recIndx, distrRecState);
			}
			
			//transient states, switching to recurrent
			if (choicesRec != null) { //MEC state
				for (Entry<Integer, Double> choiceEntry : choicesRec) {
					Iterator<Entry<Integer, Double>> iterator = model.getTransitionsIterator(i, choiceEntry.getKey());
					while(iterator.hasNext()) {
						Entry<Integer,Double> transitionEntry = iterator.next();
						distrTranState.add(transitionEntry.getKey(),
								switchProb[i] * choiceEntry.getValue() * transitionEntry.getValue());
					}
				}
			}
			
			//transitent states, not switching
			for (Entry<Integer, Double> choiceEntry : choicesTran) {
				Iterator<Entry<Integer, Double>> iterator = model.getTransitionsIterator(i, choiceEntry.getKey());
				while(iterator.hasNext()) {
					Entry<Integer,Double> transitionEntry = iterator.next();
					distrTranState.add(transitionEntry.getKey(),
							(1-switchProb[i]) * choiceEntry.getValue() * transitionEntry.getValue());
				}
			}

			mdp.addChoice(tranIndx, distrTranState);
		}

		// setting initial state for the game
		mdp.addInitialState(0);

		return mdp;
	}


	public String getInfo()
	{
		return info;
	}


	public void setInfo(String info)
	{
		this.info = info;
	}


	public int getMemorySize()
	{
		return (switchProb == null) ? 0 : 2;
	}


	public String getType()
	{
		return "Stochastic update strategy.";
	}


	public Object getCurrentMemoryElement()
	{
		return isTrans;
	}


	public void setMemory(Object memory) throws InvalidStrategyStateException
	{
		if (memory instanceof Boolean) {
			this.isTrans = (boolean) memory;
		} else
			throw new InvalidStrategyStateException("Memory element has to be a boolean.");
	}


	public String getStateDescription()
	{
		StringBuilder s = new StringBuilder();
		if (switchProb != null) {
			s.append("Stochastic update strategy.\n");
			s.append("Memory size: 2 (transient/recurrent phase).\n");
			s.append("Current memory element: ");
			s.append((this.isTrans) ? "transient." : "recurrent." );
		} else {
			s.append("Memoryless randomised strategyy.\n");
			s.append("Current state is ");
			s.append((this.isTrans) ? "transient." : "recurrent." );
		}
		return s.toString();
	}


	public int getInitialStateOfTheProduct(int s)
	{
		return 0;//TODO
	}

	public void export(PrismLog out)
	{

	}

	@Override
	public void exportActions(PrismLog out)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void initialise(int s)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void update(Object action, int s)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getChoiceAction()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clear()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exportIndices(PrismLog out)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exportInducedModel(PrismLog out)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exportInducedModel(PrismLog out, int precision) {

	}

	@Override
	public void exportDotFile(PrismLog out) {
		Strategy.super.exportDotFile(out);
	}

	@Override
	public void exportDotFile(PrismLog out, int precision) {

	}

	;
}
