package explicit;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import parser.type.TypeBool;
import parser.type.TypeDouble;
import prism.Operator;
import prism.Point;
import prism.PrismException;
import prism.PrismSettings;
import solvers.LpSolverProxy;
import solvers.SolverProxyInterface;
import strat.MultiLongRunStrategy;

import explicit.rewards.MDPRewards;

/**
 * This class contains functions used when solving
 * multi-objective mean-payoff (=long run, steady state)
 * problem for MDPs. It provides a LP encoding taken from
 * http://qav.cs.ox.ac.uk/bibitem.php?key=BBC+11 
 * (Two views on Multiple Mean-Payoff Objectives in Markov Decision Processes).
 * 
 * Note that we use a bit different notation here and refer to y_s variables as
 * Z, not to confuse them with y_{s,a}.
 * @author vojfor
 *
 */

public class MultiLongRun {
	MDP mdp;
	List<BitSet> mecs;
	ArrayList<MDPRewards> rewards;
	ArrayList<Operator> operators;
	ArrayList<Double> bounds;

	/**
	 * The instance providing access to the LP solver.
	 */
	SolverProxyInterface solver;
	
	boolean initialised = false;

	/**
	 * Number of continuous variables in the LP instance
	 */
	private int numRealLPVars;
	
	/**
	 * Number of binary in the LP instance
	 */
	private int numBinaryLPVars;
	
	/**
	 * number of states that are contained in some mec.
	 */
	private int numMecStates;

	/**
	 * LP solver to be used
	 */
	private String method;
	
	/**
	 * xOffset[i] is the solver's variable (column) for the first action of state i, i.e. for x_{i,0}
	 */
	private int[] xOffsetArr;
	
	/**
	 * yOffset[i] is the solver's variable (column) for the first action of state i, i.e. for x_{i,0}
	 */
	private int[] yOffsetArr;
	
	private int[] sOffsetArr;
	
	private int[] tOffsetArr;
	
	private int epsilonVarIndex;
	
	/**
	 * zIndex[i] is the z variable for the state i (i.e. y_i in LICS11 terminology). 
	 */
	private int[] zIndex;
	
	/**
	 * qIndex[i] is the q variable for the state i (binary variable ensuring memorylessnes, not present in LICS11). 
	 */
	private int[] qIndex;
	
	
	/**
	 * The default constructor.
	 * @param mdp The MDP to work with
	 * @param rewards Rewards for the MDP
	 * @param operators The operators for the rewards, instances of {@see prism.Operator}
	 * @param bounds Lower/upper bounds for the rewards, if operators are >= or <=
	 * @param method Method to use, should be a valid value for
	 *        {@see PrismSettings.PrismSettings.PRISM_MDP_MULTI_SOLN_METHOD}
	 */
	public MultiLongRun(MDP mdp, ArrayList<MDPRewards> rewards,
			ArrayList<Operator> operators, ArrayList<Double> bounds, String method) {
		this.mdp = mdp;
		this.rewards = rewards;
		this.operators = operators;
		this.bounds = bounds;
		this.method = method;
	}
	
	/**
	 * Creates a new solver instance, based on the argument {@see #method}.
	 * @throws PrismException If the jar file providing access to the required LP solver is not found.
	 */
	private void initialiseSolver(boolean memoryless) throws PrismException
	{
		try { //below Class.forName throws exception if the required jar is not present
			if (method.equals("Linear programming")) {
				//create new solver
				solver = new LpSolverProxy(this.numRealLPVars, this.numBinaryLPVars);
			} else if (method.equals("Gurobi")) {
				Class<?> cl = Class.forName("solvers.GurobiProxy");
				solver = (SolverProxyInterface) cl.getConstructor(int.class, int.class).newInstance(this.numRealLPVars, this.numBinaryLPVars);
			}
			else throw new UnsupportedOperationException("The given method for solving LP programs is not supported: " + method);
		} catch (ClassNotFoundException ex) {
			throw new PrismException("Cannot load the class required for LP solving. Was gurobi.jar file present in compilation time and is it present now?");
		} catch (NoClassDefFoundError e) {
			throw new PrismException("Cannot load the class required for LP solving, it seems that gurobi.jar file is missing. Is GUROBI_HOME variable set properly?");
		} catch (InvocationTargetException e) {
			String append = "";
			if (e.getCause() != null) {
				append = "The message of parent exception is: " + e.getCause().getMessage();
			}
			
			throw new PrismException("Problem when initialising an LP solver. " +
					"InvocationTargetException was thrown" +
					"\n Message: " + e.getMessage() + "\n" + append);
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException
				| NoSuchMethodException
				| SecurityException e) {
			throw new PrismException("Problem when initialising an LP solver. " +
					"It appears that the JAR file is present, but there is some problem, because the exception of type " + e.getClass().toString() +
					" was thrown. Message: " + e.getMessage());
		}
	}
	
	/**
	 * computes the set of end components and stores it in {@see #mecs}
	 * @throws PrismException
	 */
	private void computeMECs() throws PrismException
	{
		ECComputer ecc = ECComputerDefault.createECComputer(null, mdp);
		ecc.computeMECStates();
		mecs = ecc.getMECStates();
		
		this.numMecStates = 0;
		for (BitSet b : mecs)
			this.numMecStates += b.cardinality();
	}
	
	/**
	 * The LICS11 paper considers variables y_{s,a}, y_s and x_{s,a}. The solvers mostly
	 * access variables just by numbers, starting from 0 or so. We use
	 * {@see #getVarY(int, int)}, {@see #getVarZ(int)} and {@see #getVarX(int, int)}
	 * to get, for a variable y_{s,a}, y_s and x_{s,a}, respectively, in the LICS11 sense,
	 * a corresponding variable (i.e. column) in the linear program.
	 * 
	 * This method does all the required initialisations that are required for the
	 * above mentioned methods to work correctly.
	 */
	private void computeOffsets(boolean memoryless)
	{
		this.xOffsetArr = new int[mdp.getNumStates()];
		int current = 0; //we start from 1, because lpsolve ignores first		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			boolean isInMEC = false;
			for (BitSet b : this.mecs)
			{
				if (b.get(i)) {
					isInMEC = true;
					break;
				}
			}
			if (isInMEC) {
				xOffsetArr[i] = current;
				current += mdp.getNumChoices(i);
			} else {
				xOffsetArr[i] = Integer.MIN_VALUE; //so that when used in array, we get exception
			}
		}
		
		this.yOffsetArr = new int[mdp.getNumStates()];
		for (int i = 0; i < mdp.getNumStates(); i++) {
			yOffsetArr[i] = current;
			current += mdp.getNumChoices(i);
		}
		
		this.zIndex = new int[mdp.getNumStates()];
		
		for (int i = 0; i < mdp.getNumStates(); i++) {			
			zIndex[i] = (isMECState(i)) ? current++ : Integer.MIN_VALUE;
		}
		
		if (!memoryless) {
			this.numRealLPVars = current;
			return; //end here if we don't do memoryless strategy, ow we do MILP
		}
		
		//position of the epsilon variable
		this.epsilonVarIndex = current++;
		this.numRealLPVars = current;
		
		//binary variables
		
		this.sOffsetArr = new int[mdp.getNumStates()];		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if (isMECState(i)) {
				sOffsetArr[i] = current;
				current += mdp.getNumChoices(i);
			} else {
				sOffsetArr[i] = Integer.MIN_VALUE; //so that when used in array, we get exception
			}
		}
		
		this.tOffsetArr = new int[mdp.getNumStates()];		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if (isMECState(i)) {
				tOffsetArr[i] = current;
				current += mdp.getNumChoices(i);
			} else {
				tOffsetArr[i] = Integer.MIN_VALUE; //so that when used in array, we get exception
			}
		}
				
		this.qIndex = new int[mdp.getNumStates()];
		
		for (int i = 0; i < mdp.getNumStates(); i++) {			
			qIndex[i] = (isMECState(i)) ? current++ : Integer.MIN_VALUE;
		}
		
		this.numBinaryLPVars = current - this.numRealLPVars;
	}

	/**
	 * Returns true if the state given is in some MEC.
	 */
	private boolean isMECState(int state)
	{
		return xOffsetArr[state] != Integer.MIN_VALUE;
	}
	
	/**
	 * Names all variables, useful for debugging.
	 * @throws PrismException
	 */
	private void nameLPVars(boolean memoryless) throws PrismException
	{
		int current = 0;
		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				for(int j = 0; j < mdp.getNumChoices(i); j++) {
					String name =  "x" + i + "c" + j;
					solver.setVarName(current + j, name);
					solver.setVarBounds(current + j, 0.0, 1.0);
				}
				current += mdp.getNumChoices(i);
			}
		}
		
		for (int i = 0; i < mdp.getNumStates(); i++) {

			for(int j = 0; j < mdp.getNumChoices(i); j++) {
				String name =  "y" + i + "c" + j;
				solver.setVarName(current + j, name);
				solver.setVarBounds(current + j, 0.0, Double.MAX_VALUE);
			}
			current += mdp.getNumChoices(i);
		}
		
		for(int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				String name =  "z" + i;
				solver.setVarName(current, name);
				solver.setVarBounds(current++, 0.0, Double.MAX_VALUE);
			}
		}
		
		if (!memoryless)
			return; //there are no more variables
		
		solver.setVarName(current++, "eps");
		
		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				for(int j = 0; j < mdp.getNumChoices(i); j++) {
					String name =  "s" + i + "c" + j;
					solver.setVarName(current + j, name);
					solver.setVarBounds(current + j, 0.0, 1.0);
				}
				current += mdp.getNumChoices(i);
			}
		}
		
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				for(int j = 0; j < mdp.getNumChoices(i); j++) {
					String name =  "t" + i + "c" + j;
					solver.setVarName(current + j, name);
					solver.setVarBounds(current + j, 0.0, Double.MAX_VALUE);
				}
				current += mdp.getNumChoices(i);
			}
		}
		
		for(int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				String name =  "q" + i;
				solver.setVarName(current, name);
				solver.setVarBounds(current++, 0.0, Double.MAX_VALUE);
			}
		}
	}
	
	/**
	 * Adds a row to the linear program, saying
	 * "all switching probabilities z must sum to 1". See LICS11 paper for details. 
	 * @throws PrismException
	 */
	private void setZSumToOne() throws PrismException {
		//NOTE: there is an error in the LICS11 paper, we need to sum over MEC states only.
		HashMap<Integer, Double> row = new HashMap<Integer,Double>();
		
		for(BitSet b : this.mecs) {
			for (int i = 0; i < b.length(); i++) {
				if (b.get(i)) {
					int index = getVarZ(i);
					row.put(index,1.0);
				}
			}
		}
		
		solver.addRowFromMap(row, 1.0, SolverProxyInterface.EQ, "sum");
	}
	
	/**
	 * Returns a hashmap giving a lhs for the equation for a reward {@code idx}.
	 * (i,d) in the hashmap says that variable i is multiplied by d. If key i is not
	 * present, it means 0.
	 * @param idx
	 * @return
	 */
	private HashMap<Integer, Double> getRowForReward(int idx, int mecIdx) {
		HashMap<Integer, Double> row = new HashMap<Integer,Double>();
		for(int state = 0; state < mdp.getNumStates(); state++) {
			if(isMECState(state)) {
				if (mecIdx != -1 && !mecs.get(mecIdx).get(state))
					continue;
				
				for(int i = 0; i < mdp.getNumChoices(state); i++) {
						int index = getVarX(state,i);
						double val = 0;
						if (row.containsKey(index))
							val = row.get(index);
						val += this.rewards.get(idx).getStateReward(state);
						val += this.rewards.get(idx).getTransitionReward(state, i);
						row.put(index, val);
				}
			}
		}
		
		return row;
	}
	
	/**
	 * Adds a row to the linear program saying that reward {@code idx} must have
	 * at least/most required value (given in the constructor to this class)
	 * @param idx
	 * @return
	 */
	private void setEqnForReward(int idx) throws PrismException {
		int op = 0;
		if (operators.get(idx) == Operator.R_GE) {
			op = SolverProxyInterface.GE;
		} else if (operators.get(idx) == Operator.R_LE) {
			op = SolverProxyInterface.LE;
		} else {//else it's min/max and we don't do anything
			return;
		}
		
		HashMap<Integer, Double> row = getRowForReward(idx,-1);
		double bound = this.bounds.get(idx);	
		
		solver.addRowFromMap(row, bound, op, "r" + idx);
	}
	
	/**
	 * Adds an equation to the linear program saying that for mec {@code mecIds},
	 * the switching probabilities in sum must be equal to the x variables in sum.
	 * See the LICS 11 paper for details.
	 * @param mecIdx
	 * @throws PrismException
	 */
	private void setEqnForMECLink(int mecIdx) throws PrismException
	{
		HashMap<Integer, Double> row = new HashMap<Integer, Double>();
		BitSet bs = this.mecs.get(mecIdx);
		
		//TODO reimplement using nextSetBit
		for (int state = 0; state < bs.length(); state++) {
			if (bs.get(state)) {
				//X
				for(int i = 0; i < mdp.getNumChoices(state); i++) {
					int index = getVarX(state, i);
					row.put(index, 1.0);					
				}
				
				//Z
				int index = getVarZ(state);
				row.put(index, -1.0);

			}
		}
		
		solver.addRowFromMap(row, 0, SolverProxyInterface.EQ, "m" + mecIdx);
	}
	
	/**
	 * These are the variables y_{state,action} from the paper. See {@see #computeOffsets()} for more details.
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarX(int state, int action)
	{
		return xOffsetArr[state] + action;
	}
	/**
	 * These are the variables x_{state,action} from the paper. See {@see #computeOffsets()} for more details.
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarY(int state, int action)
	{
		return yOffsetArr[state] + action;
	}

	/**
	 * These are the variables y_state from the paper. See {@see #computeOffsets()} for more details.
	 * @param state
	 * @return
	 */
	private int getVarZ(int state)
	{
		return zIndex[state];
	}
	
	private int getVarS(int state, int action)
	{
		return sOffsetArr[state] + action;
	}
	
	private int getVarT(int state, int action)
	{
		return tOffsetArr[state] + action;
	}
	
	/**
	 * TODO
	 */
	private int getVarQ(int state)
	{
		return qIndex[state];
	}
	
	private int getVarEpsilon()
	{
		return this.epsilonVarIndex;
	}
	
	/**
	 * Adds all rows to the LP program that give requirements
	 * on the steady-state distribution (via x variables)
	 * @throws PrismException
	 */
	private void setXConstraints() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, HashMap<Integer, Double>> map = new HashMap<Integer, HashMap<Integer,Double>> ();
		for(int state = 0; state < mdp.getNumStates(); state++) {
			if (isMECState(state))
				map.put(state, new HashMap<Integer, Double>());
		}

		//outflow
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (!isMECState(state))
				continue;
			
			for(int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarX(state, i);
					map.get(state).put(index, -1.0);
			}
		}
			
		//inflow
		for(int preState = 0; preState < mdp.getNumStates(); preState++) {
			if (!isMECState(preState))
				continue;

			for(int i = 0; i < mdp.getNumChoices(preState); i++) {
				int index = getVarX(preState,i);
				Iterator<Entry<Integer,Double>> it =  mdp.getTransitionsIterator(preState, i);
				while(it.hasNext()) {
					Entry<Integer,Double> en = it.next();
					
					if(!isMECState(en.getKey()))
						continue;
							
					Map<Integer, Double> row = map.get(en.getKey());
					assert(row != null); //we created mec rows just aboved

					double val = 0;
					if(row.containsKey(index))
						val += map.get(en.getKey()).get(index);
					
					if (val + en.getValue() != 0)
						row.put(index, val + en.getValue());
					else if (row.containsKey(index))
						row.remove(index);
					//System.out.println("just added " + val + "+" + en.getValue()));
				}
			}
		}
		
		//fill in
		double timeLastDebug = System.currentTimeMillis();
		for(int state : map.keySet()) {
			solver.addRowFromMap(map.get(state), 0, SolverProxyInterface.EQ, "x" + state);
		}
	}
		
	private void setZXLink() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, HashMap<Integer, Double>> map = new HashMap<Integer, HashMap<Integer,Double>> ();
		for(int state = 0; state < mdp.getNumStates(); state++) {
			if (isMECState(state))
				map.put(state, new HashMap<Integer, Double>());
		}

		//outflow
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (!isMECState(state))
				continue;
			map.get(state).put(getVarZ(state), 1.0);
			for(int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarX(state, i);
				map.get(state).put(index, -1.0);
			}
		}
	
		//fill in
		double timeLastDebug = System.currentTimeMillis();
		for(int state : map.keySet()) {
			solver.addRowFromMap(map.get(state), 0, SolverProxyInterface.EQ, "zx" + state);
		}
	}
	
	private void setSConstraint(int state, int choice) throws PrismException {
		HashMap<Integer, Double> map = new HashMap<Integer,Double>();
		map.put(getVarY(state, choice), 1.0);
		map.put(getVarS(state, choice), 1.0);
		solver.addRowFromMap(map, 1.0, SolverProxyInterface.LE, "s" + state + "c" + choice);
	}
	
	private void setTConstraint(int state, int choice) throws PrismException {
		HashMap<Integer, Double> map = new HashMap<Integer,Double>();
		map.put(getVarX(state, choice), 1.0);
		map.put(getVarT(state, choice), -1.0);
		map.put(getVarEpsilon(), -1.0);
		solver.addRowFromMap(map, -1.0, SolverProxyInterface.GE, "t" + state + "c" + choice);
	}
	
	private void setQConstraint(int state) throws PrismException {
		HashMap<Integer, Double> map = new HashMap<Integer,Double>();
		
		for(int i = 0; i < mdp.getNumChoices(state); i++) {
			int index = getVarX(state, i);
			map.put(index, 1.0);
		}
		map.put(getVarQ(state), 1.0);
		
		solver.addRowFromMap(map, 1.0, SolverProxyInterface.LE, "q" + state);
	}
	
	private void setSTQConstraintLink(int state, int choice) throws PrismException {
		HashMap<Integer, Double> map = new HashMap<Integer,Double>();
		
		map.put(getVarS(state, choice), 1.0);
		map.put(getVarT(state, choice), 1.0);
		map.put(getVarQ(state), 1.0);
		
		solver.addRowFromMap(map, 1.0, SolverProxyInterface.GE, "stq" + state);
	}
	
	/**
	 * For every state s: "q >= sum_a y_{s,a}" and "q + sum_a x_{s,a} <=1".
	 * This ensures that either sum_a y_{s,a} or sum_a x_{s,a} is nonzero.
	 * @throws PrismException
	 */
	private void setSTQConstraints() throws PrismException {
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (!isMECState(state))
				continue;
			
			for(int i = 0; i < mdp.getNumChoices(state); i++) {
				setSConstraint(state, i);
				setTConstraint(state, i);
				setSTQConstraintLink(state, i);
			}
			
			setQConstraint(state);
		}
	}
	
	
	/**
	 * Adds all rows to the LP program that give requirements
	 * on the MEC reaching probability (via y variables)
	 * @throws PrismException
	 */
	private void setYConstraints() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, Double>[] map = (HashMap<Integer, Double>[]) new HashMap[mdp.getNumStates()];
		for(int state = 0; state < mdp.getNumStates(); state++) {
			map[state] = new HashMap<Integer, Double>();
		}
			
		int initialState = mdp.getInitialStates().iterator().next();

		for(int state = 0; state < mdp.getNumStates(); state++) {

			//outflow y
			for(int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarY(state, i);
				map[state].put(index, -1.0);
			}
			
			//outflow z
			if (isMECState(state))
			{
				int idx = getVarZ(state);
				map[state].put(idx, -1.0);
			}
		}

		//inflow
		for(int preState = 0; preState < mdp.getNumStates(); preState++) {
			for(int i = 0; i < mdp.getNumChoices(preState); i++) {
				int index = getVarY(preState,i);
				Iterator<Entry<Integer,Double>> it =  mdp.getTransitionsIterator(preState, i);
				while(it.hasNext()) {
					Entry<Integer,Double> en = it.next();
					double val = 0;
					if(map[en.getKey()].containsKey(index))
						val += map[en.getKey()].get(index);
					
					if (val + en.getValue() != 0)
						map[en.getKey()].put(index, val + en.getValue());
					else if (map[en.getKey()].containsKey(index))
						map[en.getKey()].remove(index);
					//System.out.println("just added " + val + "+" + en.getValue()));
				}
			}
		}
		
		double timeLastDebug = System.currentTimeMillis();
		//fill in

		for(int state = 0; state < mdp.getNumStates(); state++) {			
			solver.addRowFromMap(map[state], (initialState == state) ? -1.0 : 0, SolverProxyInterface.EQ, "y" + state);
		}
	}
	
	/**
	 * Initialises the solver and creates a new instance of
	 * multi-longrun LP, for the parameters given in constructor.
	 * Objective function is not set at all, no matter if it is required.
	 * @throws PrismException
	 */
	public void createMultiLongRunLP(boolean memoryless) throws PrismException {
		if (!initialised) {
			System.out.println("Computing end components.");
			computeMECs();
			System.out.println("Finished computing end components.");				
			computeOffsets(memoryless);
			initialised = true;
		}
		
		double solverStartTime = System.currentTimeMillis(); 
		
		initialiseSolver(memoryless);
					
		nameLPVars(memoryless);
		
		//Transient flow
		setYConstraints();
		//Recurrent flow
		setXConstraints();
		//Ensuring everything reaches an end-component
		setZSumToOne();		
		
		if (memoryless) {//add binary contstraints ensuring memorylessness
			setZXLink();
			setSTQConstraints();
		}
		else 
		{
			//Linking the two kinds of flow
			for(int i = 0; i < this.mecs.size(); i++) {
				setEqnForMECLink(i);
			}
		}
		
		//Reward bounds
		for(int i = 0; i < this.rewards.size(); i++) {	
			setEqnForReward(i);
		}
		
		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP problem construction finished in " + time + " s.");
	}
	
	/**
	 * Solves the multiobjective problem for constraint only, or numerical (i.e. no Pareto) 
	 * @return
	 * @throws PrismException
	 */
	public StateValues solveDefault() throws PrismException
	{
		boolean isConstraintOnly = true;
		
		//Reward bounds
		for(int i = 0; i < this.rewards.size(); i++) {	
			if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
				continue;
			}
			
			if(!isConstraintOnly) {
					throw new PrismException("Incorrect call to solveDefault (multiple numerical objectives)"); //TODO better exception type
			}
			
			HashMap<Integer, Double> row = getRowForReward(i,-1);
			solver.setObjFunct(row, operators.get(i) == Operator.R_MAX);
			isConstraintOnly = false;
		}
		
		/*System.out.println("LP variables: " + solver.getNcolumns());
		System.out.println("LP constraints: " + solver.getNrows());*/
		
		double solverStartTime = System.currentTimeMillis();
		
		solver.solve();
		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP solving took " + time + " s.");
			
		if (isConstraintOnly) {//We should return bool type
			StateValues sv = new StateValues(TypeBool.getInstance(), mdp);
			sv.setBooleanValue(mdp.getFirstInitialState(), solver.getBoolResult());
			return sv;
		} else {	
			StateValues sv = new StateValues(TypeDouble.getInstance(), mdp);
			sv.setDoubleValue(mdp.getFirstInitialState(), solver.getDoubleResult());
			return sv;
		}	
	}
	
	/**
	 * Solves the memoryless multiobjective problem for constraint only, or numerical (i.e. no Pareto) 
	 * @return
	 * @throws PrismException
	 */
	public StateValues solveMemoryless() throws PrismException
	{		
		//Reward bounds
		for(int i = 0; i < this.rewards.size(); i++) {	
			if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
				continue;
			}
			else throw new PrismException("Memoryless problem cannot be solved for numerical objectives (Rmin/Rmax)"); //TODO better exception type

		}
		
		HashMap<Integer, Double> epsObj = new HashMap<Integer, Double>();
		epsObj.put(getVarEpsilon(), 1.0);
		solver.setObjFunct(epsObj, true);
		
		double solverStartTime = System.currentTimeMillis();
		
		boolean value = solver.solveIsPositive();
		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP solving took " + time + " s.");
			
		StateValues sv = new StateValues(TypeBool.getInstance(), mdp);
		sv.setBooleanValue(mdp.getFirstInitialState(), value);
		return sv;	
	}
		
	private double[] computeStrategy() throws PrismException
	{
		if (!initialised) {
			initialised = true;
			computeMECs();
			computeOffsets(false);
		}
		
		//store old TODO what if called this twice?
		double[] res = solver.getVariableValues();
	
		initialiseSolver(false);
		
		//Set bounds to between 0 and 1
		/*for (int i = 1; i < this.numLPVars; i++) {
			solver.setBounds(i, 0.0, 1.0);
		}*/
		
		nameLPVars(false);
		
		//Flows for reaching end-components
		setYConstraints();		
		//bscc reaching probability
		//This is not explicitly described in the paper, one needs
		//to find it in the proofs, and use the knowledge of Etessami's paper
		//that is cited.
		for(int state = 0; state < mdp.getNumStates(); state++) {
			if(isMECState(state)) {
				HashMap<Integer, Double> map = new HashMap<Integer, Double>();
				map.put(getVarZ(state), 1.0);
				
				double sum = 0;
				for(int j = 0; j < mdp.getNumChoices(state); j++) {
					System.out.println("index for (" + state + "," + j + ") is" + getVarX(state, j));
					sum += res[getVarX(state, j)];
				}
				
				solver.addRowFromMap(map, sum, SolverProxyInterface.EQ, "xf" + state);
			}
		}
		
		int r = solver.solve();
		
		//TODO value
		
		double[] stRes = solver.getVariableValues();
		
		return stRes;
	}
	
	/**
	 * Returns the strategy for the last call to solveDefault(), or null if it was never called before,
	 * or if the strategy did not exist.
	 * @return
	 */
	public MultiLongRunStrategy getStrategy(boolean memoryless) throws PrismException
	{		
		double[] resCo = solver.getVariableValues();;
		double[] resSt;
		if (memoryless) {
			resSt = solver.getVariableValues();
		} else {
			resSt = computeStrategy();			
		}
		
		int numStates = this.mdp.getNumStates();
		Distribution[] transDistr = new Distribution[numStates];
		Distribution[] recDistr = new Distribution[numStates];
		double[] switchProb = new double[numStates];
		
		for(int i = 0; i < numStates; i++) {
			double transSum = 0;
			double recSum = 0;
			for (int j = 0; j < this.mdp.getNumChoices(i); j++) {
				transSum += resSt[getVarY(i, j)];
				if (isMECState(i))
					recSum += resCo[getVarX(i, j)];
			}
			
			double switchExp = 0.0;
			if(isMECState(i))
				 switchExp = resSt[getVarZ(i)];
						
			
			if (transSum > 0 && (!memoryless || !isMECState(i) || recSum == 0))
				transDistr[i] =  new Distribution();
			if (recSum > 0 && isMECState(i))
				recDistr[i] = new Distribution();
			
			assert(!memoryless || transDistr[i] == null || recDistr[i] == null); //in memoryless one will be null
			
			for (int j = 0; j < this.mdp.getNumChoices(i); j++) {
				if (transDistr[i] != null)
					transDistr[i].add(j, resSt[getVarY(i,j)] / transSum);
				if (recDistr[i] !=null)
					recDistr[i].add(j, resCo[getVarX(i,j)] / recSum);
				if (switchExp > 0)
					switchProb[i] = switchExp / (transSum + switchExp);
			}
			
			System.out.println("r d for " + i + " is " + recDistr[i]);
			//else the state is not reachable. TODO should we still do something?
		}
				
		//TODO sometimes return null?
		MultiLongRunStrategy strat;
		if (memoryless)
			strat = new MultiLongRunStrategy(transDistr, recDistr);
		else
			strat = new MultiLongRunStrategy(transDistr, switchProb, recDistr);
		return strat;
	}

	/**
	 * For the given 2D weights, get a point p on that is maximal for these weights, i.e.
	 * there is no p' with p.weights<p'.weights.
	 * @param weights
	 * @return
	 * @throws PrismException
	 */
	public Point solveMulti(Point weights) throws PrismException {
		if (weights.getDimension() != 2) {
			throw new UnsupportedOperationException("Multilongran can only create 2D pareto curve.");
		}
		HashMap<Integer, Double> weightedMap = new HashMap<Integer, Double>();
		//Reward bounds
		int numCount = 0;
		ArrayList<Integer> numIndices = new ArrayList<Integer>();
		for(int i = 0; i < this.rewards.size(); i++) {					
			if (operators.get(i) == Operator.R_MAX) {
				HashMap<Integer, Double> map = getRowForReward(i,-1);
				for(Entry<Integer, Double> e : map.entrySet())
				{
					double val = 0;
					if (weightedMap.containsKey(e.getKey()))
						val=weightedMap.get(e.getKey());
					weightedMap.put(e.getKey(), val + (weights.getCoord(numCount) * e.getValue())); 
				}
				numCount++;
				numIndices.add(i);
			} else if (operators.get(i) == Operator.R_MIN) {
				throw new PrismException("Only maximising rewards in Pareto curves are currently supported (note: you can multiply your rewards by 1 and change min to max"); //TODO min
			}
		}
		
		solver.setObjFunct(weightedMap, true);
		
		int r = solver.solve();
		double[] resultVars = solver.getVariableValues();
		
		Point p = new Point(2);
		
		if (r == lpsolve.LpSolve.INFEASIBLE) {//TODO other results
			throw new PrismException("TODO");
		} else if (r == lpsolve.LpSolve.OPTIMAL) {
			new Point(2);
			
			for(int i = 0; i < weights.getDimension(); i++) {
				double res = 0;
				HashMap<Integer, Double> rewardEqn = getRowForReward(numIndices.get(i),-1);
				for (int j = 0; j < resultVars.length; j++) {
					if (rewardEqn.containsKey(j)) {					
						res += rewardEqn.get(j) * resultVars[j];
					}
				}
				p.setCoord(i, res);
			}
		} else {
			throw new PrismException("Unexpected result of LP solving: " + r);
		}
		return p;
	}
/*	
	private double[] getExtremaInMec(int idx) throws PrismException
	{
		double[] result = new double[2]; //(min, max)
		
		
		initialiseSolver();
		
		setXConstraints();
		
		HashMap<Integer, Double> rewRow = getRowForReward(0, idx);
		
		solver.setObjFunct(rewRow, false);
		
		solver.solve();
		
		result[0] = solver.getDoubleResult()
		
        solver.setObjFunct(rewRow, true);
		
		solver.solve();
		
		result[1] = solver.getDoubleResult();
		
		return result;
		
	}
	
	private StateValues solveVariance() throws PrismException
	{
		//Get Min/Max values in every mec
		double[] minInMecs = new double[mecs.size()];
		double[] maxInMecs = new double[mecs.size()];
		for (int i = 0; i < mecs.size(); i++) {
			double[] r = getExtremaInMec(i);
			minInMecs[i] = r[0];
			maxInMecs[i] = r[1];
		}
		
		//Construct the linear program for the whole problem
		initialiseSolver();
		setYConstraints();
		
		//Solve
	
	}*/
}
