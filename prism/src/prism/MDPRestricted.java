//==============================================================================
//	
//	Copyright (c) 2016-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de>
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================


package prism;

import jdd.JDD;
import jdd.JDDNode;
import jdd.JDDVars;
import jdd.SanityJDD;
import odd.ODDNode;
import parser.VarList;

import javax.xml.bind.SchemaOutputResolver;
import java.util.List;
import java.util.Stack;

/**
 * Transformation for obtaining the MEC restricted MDP M, given a set of states and stable transitions
 * Labels attached to the original model are currently effectively stripped,
 * i.e., they are replaced with JDD.ZERO. In the future, more advanced treatment
 * may be added.
 * <br>
 */
public class MDPRestricted implements ModelTransformation<NondetModel,NondetModel>
{
	private NondetModel originalModel;
	private NondetModel transformedModel;
	private MDPRestrictOperator transform;
	private JDDNode transformedStatesOfInterest;

	/** Private constructor */
	private MDPRestricted(NondetModel originalModel, NondetModel transformedModel, MDPRestrictOperator transform, JDDNode transformedStatesOfInterest)
	{
		this.originalModel = originalModel;
		this.transformedModel = transformedModel;
		this.transform = transform;
		this.transformedStatesOfInterest = transformedStatesOfInterest;
	}

	@Override
	public NondetModel getOriginalModel()
	{
		return originalModel;
	}

	@Override
	public NondetModel getTransformedModel()
	{
		return transformedModel;
	}

	@Override
	public void clear()
	{
		transformedModel.clear();
		transform.clear();
		JDD.Deref(transformedStatesOfInterest);
	}

	@Override
	public StateValues projectToOriginalModel(StateValues svTransformedModel) throws PrismException
	{
		StateValuesMTBDD sv = svTransformedModel.convertToStateValuesMTBDD();
		JDDNode v = sv.getJDDNode().copy();
		sv.clear();

		v = transform.liftFromRepresentatives(v);

		return new StateValuesMTBDD(v, transform.originalModel);
	}

	/** Map the given state set from the original model to the quotient model */
	public JDDNode mapStateSetToQuotient(JDDNode S)
	{
		return transform.mapStateSet(S);
	}

	@Override
	public JDDNode getTransformedStatesOfInterest()
	{
		return transformedStatesOfInterest;
	}


	/**
	 * Compute the quotient MDP for the given list of equivalence classes (the classes have to be disjoint).
	 *
	 * <br>[ REFs: <i>result</i>, DEREFs: equivalenceClasses, statesOfInterest ]
	 */
	public static MDPRestricted transform(PrismComponent parent, final NondetModel model, JDDNode mec)
			throws PrismException
	{
		final MDPRestrictOperator transform = new MDPRestrictOperator(parent, model,mec);
		final JDDNode transformedmec = transform.mapStateSet(mec);

		final NondetModel mdprestricted = model.getTransformed(transform);

		return new MDPRestricted(model, mdprestricted, transform, transformedmec);
	}

	/** The transformation operator for the MDPQuotient operation. */
	public static class MDPRestrictOperator extends NondetModelTransformationOperator {
		private JDDNode stateActionWithSelfLoop;

		private JDDNode stateActionWithoutSelfLoop;
		private JDDNode mec;
		/**
		 * A symbolic mapping (0/1-ADD) from states (row vars) to their representative (col vars)
		 * in the restricted model.<br>
		 * For states in equivalence classes map to the representative, states not contained in
		 * an equivalent class are mapped to themselves.
		 */
		private JDDNode map;

		/** State set: states not contained in equivalence classes */
		private JDDNode notMEC;


		/** The transition relation of the quotient model */
		private JDDNode newTrans;
		/** The 0/1-transition relation of the quotient model */
		private JDDNode newTrans01;

		/** New transition matrix already computed? */
		private boolean computed = false;

		/** The parent prism component (for logging) */
		private PrismComponent parent;

		/**
		 * n JDDVars, where n = number of row vars in the original model.
		 * Used to store the original originating state of a transition in
		 * the quotient model.
		 */
		private JDDVars actFromStates;

		/** Debug: Verbose output? */
		private boolean verbose = false;

		/** Constructor */
		public MDPRestrictOperator(PrismComponent parent, NondetModel model, JDDNode mec)
		{
			super(model);

			this.mec = mec;
			this.parent = parent;

			map = JDD.Constant(0);

			// all states not in EC
			notMEC = JDD.And(model.getReach().copy(),
					JDD.Not(mec.copy()));

			// map all states inside EC to themselfs
			map = JDD.ITE(mec.copy(),
					JDD.Identity(model.getAllDDRowVars(), model.getAllDDColVars()),
					map);

			stateActionWithoutSelfLoop = JDD.Constant(0);

			for (int i = 0  ; i < originalModel.getNumStates();i++){
				JDDNode state = originalModel.getReach();
				JDDNode stateCol = JDD.PermuteVariables(state.copy(), model.getAllDDRowVars(), model.getAllDDColVars());
				JDDNode transFromState01 = JDD.And(state.copy(), model.getTrans01().copy());
				JDDNode noSelfLoop = JDD.Times(transFromState01.copy(), stateCol.copy());
				stateActionWithoutSelfLoop  = JDD.Or(stateActionWithoutSelfLoop, JDD.ThereExists(noSelfLoop, originalModel.getAllDDColVars()));
			}



		}

		@Override
		public void clear()
		{
			JDD.Deref(mec);
			JDD.Deref(stateActionWithSelfLoop);
			JDD.Deref(map);
			JDD.Deref(notMEC);
			if (newTrans != null) JDD.Deref(newTrans);
			if (newTrans01 != null) JDD.Deref(newTrans01);
			super.clear();
			if (actFromStates != null)
				actFromStates.derefAll();
		}

		@Override
		public int getExtraStateVariableCount()
		{
			return 0;
		}

		@Override
		public int getExtraActionVariableCount()
		{
			// 1 bit (tau) for normal vs special action (leaving a representative),
			// numDDRowVar bits to remember original originating state
			// for the states leaving a representative
			return originalModel.getNumDDRowVars() + 1;
		}

		@Override
		public void hookExtraActionVariableAllocation(JDDVars extraActionVars)
		{
			// call super to store extraActionVars
			super.hookExtraActionVariableAllocation(extraActionVars);

			// initialize actFromStates
			actFromStates = new JDDVars();
			for (int i = 1; i < extraActionVars.n(); i++) {
				actFromStates.addVar(extraActionVars.getVar(i).copy());
			}
		}

		/** Get the tau action variable */
		public JDDNode getTauVar()
		{
			return extraActionVars.getVar(0).copy();
		}

		/** Get the marker for a tau action */
		public JDDNode tau()
		{
			return getTauVar();
		}

		/** Get the marker for a non-tau action (!tau & zeros for all other extra action vars) */
		public JDDNode notTau()
		{
			JDDNode notTau = JDD.Not(getTauVar());
			notTau = JDD.And(notTau, actFromStates.allZero());
			return notTau;
		}

		private void compute() throws PrismException
		{

			newTrans = JDD.Constant(0);
			JDDNode factor = JDD.Constant(2);
			JDDNode trans = originalModel.getTrans().copy();


			//newTrans = JDD.Times(trans.copy(), mec.copy());


			//JDDNode transWithSelfLoop = JDD.Times(trans.copy(), stateActionWithSelfLoop.copy());
			//stateActionWithoutSelfLoop = JDD.Apply(JDD.DIVIDE ,  stateActionWithoutSelfLoop, factor);
			//JDDNode transWithoutSelfLoop = JDD.Times(trans.copy(), stateActionWithoutSelfLoop.copy());
			//JDDNode transWithAddedLoop = JDD.Apply(JDD.TIMES , transWithoutSelfLoop , JDD.Constant(0.5));

			//newTrans = JDD.Or(transWithAddedLoop.copy(), transWithSelfLoop.copy());
			//JDD.PrintMinterms(parent.getLog(), stateActionWithoutSelfLoop.copy(), "No Self Loop:" );
			//JDD.PrintMinterms(parent.getLog(), stateActionWithSelfLoop.copy(), "Self Loop:" );

			newTrans = JDD.Times(trans.copy(), notTau());
			newTrans01 = JDD.GreaterThan(newTrans.copy(), 0);

			computed = true;

			JDD.Deref(factor, trans);

		}

		/**
		 * Maps a state set from the original model to the corresponding state set
		 * in the quotient model.
		 * [ REFS: <i>result</i>, DEREFS: s ]
		 */
		public JDDNode mapStateSet(JDDNode S)
		{
			if (verbose) JDD.PrintMinterms(parent.getLog(), S.copy(), "S");
			JDDNode mapped = JDD.And(S, map.copy());
			if (verbose) JDD.PrintMinterms(parent.getLog(), mapped.copy(), "mapped");

			mapped = JDD.ThereExists(mapped, originalModel.getAllDDRowVars());
			mapped = JDD.PermuteVariables(mapped, originalModel.getAllDDColVars(), originalModel.getAllDDRowVars());

			if (verbose) JDD.PrintMinterms(parent.getLog(), mapped.copy(), "mapped (result)");
			return mapped;
		}

		@Override
		public JDDNode getTransformedTrans() throws PrismException
		{
			if (!computed) compute();
			return newTrans.copy();
		}

		@Override
		public JDDNode getTransformedStart() throws PrismException
		{
			return mapStateSet(originalModel.getStart().copy());
		}

		@Override
		public JDDNode getTransformedStateReward(JDDNode rew) throws PrismException
		{
			return rew.copy();
		}

		@Override
		public JDDNode getTransformedTransReward(JDDNode rew) throws PrismException
		{
			if (!computed) compute();

			if (SanityJDD.enabled) {
				SanityJDD.checkIsDDOverVars(rew, originalModel.getAllDDRowVars(), originalModel.getAllDDNondetVars(), originalModel.getAllDDColVars());
			}

			// outgoing actions from ECs
			if (verbose) JDD.PrintMinterms(parent.getLog(), rew.copy(), "trans rew");
			JDDNode rewFromEC = JDD.Times(rew.copy(), mec.copy());
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (1)");
			rewFromEC = JDD.PermuteVariables(rewFromEC, originalModel.getAllDDRowVars(), actFromStates);
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (2)");
			//rewFromEC = JDD.Times(tau(), rewFromEC);
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (3)");
			rewFromEC = JDD.PermuteVariables(rewFromEC, originalModel.getAllDDColVars(), originalModel.getAllDDRowVars());
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (4)");
			rewFromEC = JDD.Times(rewFromEC, map.copy());
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (5)");
			rewFromEC = JDD.SumAbstract(rewFromEC, originalModel.getAllDDRowVars());
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (6)");
			rewFromEC = JDD.Times(rewFromEC, newTrans01.copy());
			if (verbose) JDD.PrintMinterms(parent.getLog(), rewFromEC.copy(), "rewFromEC (7)");


			// transformedRew is the combination of the outgoing actions from the ECs
			// and the original actions (tagged with notTau)
			if (verbose) JDD.PrintMinterms(parent.getLog(), rew.copy(), "trans rew");
			//JDDNode transformedRew = JDD.Apply(JDD.MAX, JDD.Times(notTau(), rew.copy()), rewFromEC);
			JDDNode transformedRew = rew.copy();
			transformedRew = JDD.Times(transformedRew, newTrans01.copy());
			if (verbose) JDD.PrintMinterms(parent.getLog(), transformedRew.copy(), "transformedRew");

			return transformedRew;
		}

		@Override
		public JDDNode getTransformedTransActions()
		{
			if (originalModel.getTransActions() == null) {
				return null;
			}

			//JDDNode transActionsNormal = originalModel.getTransActions().copy();
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transActionsNormal.copy(), "transActionsNormal (1)");
			//transActionsNormal = JDD.Times(transActionsNormal, notTau());
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transActionsNormal.copy(), "transActionsNormal (2)");

			//JDDNode transActionsFromEC = originalModel.getTransActions().copy();
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transActionsFromEC.copy(), "transActionsFromEC (1)");
			// shift from states to actFromEC
			//transActionsFromEC = JDD.PermuteVariables(transActionsFromEC, originalModel.getAllDDRowVars(), actFromStates);
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transActionsFromEC.copy(), "transActionsFromEC (2)");
			//transActionsFromEC = JDD.Times(transActionsFromEC, tau());
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transActionsFromEC.copy(), "transActionsFromEC (3)");

			//JDDNode transformedTransActions = JDD.Apply(JDD.MAX, transActionsNormal, transActionsFromEC);
			//JDDNode transformedTransActions = newTrans;
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transformedTransActions.copy(), "transformedTransActions");
			JDDNode transformedTransActions = originalModel.getTransActions().copy();
			transformedTransActions = JDD.Times(transformedTransActions, JDD.ThereExists(newTrans01.copy(), originalModel.getAllDDColVars()));
			//transformedTransActions = JDD.Times(transformedTransActions, JDD.ThereExists(newTrans01.copy(), originalModel.getAllDDColVars()));

			//transformedTransActions = JDD.Times(transformedTransActions, newTrans01);
			//if (verbose) JDD.PrintMinterms(parent.getLog(), transformedTransActions.copy(), "transformedTransActions");

			//return transformedTransActions;
			return null;
		}

		@Override
		public JDDNode getTransformedLabelStates(JDDNode oldLabelStates, JDDNode transformedReach)
		{
			// we always return 'false' here, as it's difficult to decide how to label
			// the representative states if not all the states in the EC are labelled
			// the same
			return JDD.Constant(0);
		}

		/** Provide the set of reachable states */
		public JDDNode getReachableStates()
		{
			// reachable states in the quotient MDP:
			//  remove non-representative states contained in ECs
			//  from the reachable states in the original model
			//JDDNode removed = JDD.And(inEC.copy(), JDD.Not(representatives.copy()));
			//JDDNode newReach = JDD.And(originalModel.getReach().copy(), JDD.Not(removed));

			return mec;
		}

		/**
		 * Lift an ADD over row variables from the quotient model to the original model,
		 * by copying the values from the representatives to the other states in the EC.
		 */
		public JDDNode liftFromRepresentatives(JDDNode n)
		{
			// we first shift the result to the column variables
			JDDNode result = JDD.PermuteVariables(n, originalModel.getAllDDRowVars(), originalModel.getAllDDColVars());
			// combine with the map. we now have (state,representative) -> value
			result = JDD.Times(result, map.copy());
			// as there is exactly one representative per state, we can abstract using SumAbstract
			// (works for positive and negative values)
			result = JDD.SumAbstract(result, originalModel.getAllDDColVars());
			return result;
		}
	}

}
