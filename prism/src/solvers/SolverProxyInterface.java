package solvers;

import java.util.Map;
import prism.PrismException;

/**
 * Classes implementing this interface should provide access to LP solvers.
 * The intention is to have one common API for calling solvers, independent
 * of the actual LP solver used
 * @author vojfor
 *
 */
public interface SolverProxyInterface {
	public static final int EQ = 32;
	public static final int GE = 64;
	public static final int LE = 128;
	
	/**
	 * Adds new row to the problem.
	 * @param row Gives the lhs, where the entry (i,d) says that column i should have constant d. Here i is indexed from 0
	 * @param rhs Right hand side constant of the row
	 * @param op The operator, either EQ, GE or LE
	 * @param name Name to be given t
	 * @throws PrismException
	 */
	public void addRowFromMap(Map<Integer,Double> row, double rhs, int op, String name) throws PrismException;

	/**
	 * Solves the LP problem, optionally stopping when positive value is found.
	 * @return
	 * @throws PrismException
	 */
	public boolean solveIsPositive() throws PrismException;	
	
	/**
	 * Solves the LP problem
	 * @return
	 * @throws PrismException
	 */
	public int solve() throws PrismException;

	/**
	 * Returns the boolean result: optimal=true, infeasible=false. Useful when
	 * only constraints were given, and no objective.
	 * @return
	 * @throws PrismException
	 */
	public boolean getBoolResult() throws PrismException;
	
	/**
	 * Returns value of the objective function. If infeasible, returns NaN
	 * @return
	 * @throws PrismException
	 */
	public double getDoubleResult() throws PrismException;
	
	/**
	 * Sets name of the variable (=column) idx, indexed from 0. Useful for debugging.
	 * @param idx
	 * @param name
	 * @throws PrismException
	 */
	public void setVarName(int idx, String name) throws PrismException;
	
	/**
	 * Sets lower and upper bounds for the variable values
	 * @param idx The variable to be changed, indexed from .
	 * @param lo Lower bound on the value
	 * @param up Upper bound on the value
	 * @throws PrismException
	 */
	public void setVarBounds(int idx, double lo, double up) throws PrismException;
	
	/**
	 * Sets the objective function.
	 * @param row See {@see #addRowFromMap(Map, double, int, String)}
	 * @param max True for maximising, false for minimising
	 * @throws PrismException
	 */
	public void setObjFunct(Map<Integer,Double> row, boolean max) throws PrismException;
	
	/**
	 * Gets the array of variable values. Should be called only after solve was called. 
	 */
	public double[] getVariableValues() throws PrismException;
}
