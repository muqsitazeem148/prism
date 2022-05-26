package explicit;

import explicit.rewards.MDPRewards;
import prism.Point;

import java.util.List;



public class WeightedVI {

    private static final double INFINITY = Double.MAX_VALUE;
    private MDP model;
    private List<MDPRewards> rewards;
    private int n, maxIters, start_index;
    private int numObj = 2;

    private double zero_roundoff = 10e-11;

    //currently only maximizing objectives are supported
    private boolean min = false;

    public WeightedVI(MDP model, List<MDPRewards> rewards, int maxIters) {
        this.model = model;
        this.rewards = rewards;
        this.n = model.getNumStates();
        this.maxIters = maxIters;
        start_index = model.getFirstInitialState();
    }

    public WeightedVI(MDP model, List<MDPRewards> rewards) {
        this.model = model;
        this.rewards = rewards;
        this.n = model.getNumStates();
        this.maxIters = 1000;
        start_index = model.getFirstInitialState();
    }


    public Point solve(Point weightsP){

        //initialise weight array
        double[] weights = new double[weightsP.getDimension()];
        for (int i = 0 ; i< weightsP.getDimension(); i++){
            weights[i] = weightsP.getCoord(i);
        }

        Point sol =  new Point(numObj);


        double min_weight = 1;
        for (int i = 0; i < numObj ; i++)
            if (weights[i] > 0 && weights[i] < min_weight) {
                min_weight = weights[i];
            }
        double near_zero = min_weight * zero_roundoff;


        // create solution/iteration vectors
        double[] soln = new double[n];
        double[] soln2 = new double[n];
        double[][] psoln = new double[numObj][n];
        double[][] psoln2 = new double[numObj][n];
        double[] tmpsoln;

        double [] pd1 = new double[numObj];
        double [] pd2 = new double[numObj];

        // initialize solutions with 0
        for (int i = 0; i < n; i++) {
            soln[i] = 0;
            soln2[i] = 0;
            for (int j = 0; j < numObj; j++) {
                psoln[j][i] = 0;
                psoln2[j][i] = 0;
            }
        }

        //Start of the actual value iteration
        int iters = 0;
        boolean done =false, weightedDone = false , first , doneBeforeBounded = false;
        double d1,d2;

        while (!done && iters < maxIters) {
            iters++;

            // loop through states
            for (int i = 0; i < n; i++) {
                first = true;

                // first, get the decision of the adversary optimizing the combined reward
                d1 = -INFINITY;
                for (int it = 0; it < numObj; it++)
                    pd1[it] = -INFINITY;


                // loop through all choices
                for (Object action : model.getAvailableActions(i)){
                    int choice = model.getChoiceByAction(i,action);
                    // compute, for state i for this iteration,
                    // the combined and individual reward values
                    d2 = 0;
                    for (int it = 0; it < numObj; it++)
                        pd2[it] = 0;

                    // loop through transitions
                    SuccessorsIterator successors = model.getSuccessors(i, choice);
                    while(successors.hasNext()){
                        int successor = successors.nextInt();
                        // for each reward structure
                        for (int rewi = 0; rewi < numObj; rewi++) {
                            d2 += weights[rewi] * rewards.get(rewi).getTransitionReward(i, choice)* model.getTransProb(i, choice, successor);
                            pd2[rewi] += rewards.get(rewi).getTransitionReward(i, choice) * model.getTransProb(i, choice, successor);
                            pd2[rewi] += model.getTransProb(i, choice, successor) * psoln[rewi][successor];
                        }
                        d2 += model.getTransProb(i, choice, successor) * soln[successor];
                    }
                    // see if the combined reward value is the min/max so far
                    boolean pickThis = first || (min && (d2 < d1)) || (!min && (d2 > d1));

                    if (!pickThis && (d2 == d1)) {
                        for (int it = 0; it < numObj; it++) {
                            if ((min && (pd2[it] < pd1[it])) || (!min && (pd2[it] > pd1[it]))) {
                                pickThis = true;
                                break;
                            }

                        }
                    }
                    if (pickThis) {
                        // store optimal values for combined and individual rewards
                        d1 = d2;
                        for (int it = 0; it < numObj; it++) {
                            pd1[it] = pd2[it];
                        }
                    }
                    first = false;
                }


                if (d1 == -INFINITY) {
                    d1 = 0;
                    for (int it = 0; it < numObj; it++) {
                        pd1[it] = 0;
                    }
                }

                for (int it = 0; it < numObj; it++) {
                    psoln2[it][i] = pd1[it];
                }
                soln2[i] = d1;

            }

            //round small numbers to zero
            for (int o = 0; o < n; o++) {
                if (Math.abs(soln[o]) < near_zero) soln[o] = 0;
                if (Math.abs(soln2[o]) < near_zero) soln2[o] = 0;
            }
            for (int it = 0; it < numObj; it++) {
                for (int o = 0; o < n; o++) {
                    if (Math.abs(psoln[it][o]) < near_zero) psoln[it][o] = 0;
                    if (Math.abs(psoln2[it][o]) < near_zero) psoln2[it][o] = 0;
                }
            }

            // check convergence
            if (!weightedDone) {
                weightedDone = true;
                for (int i = 0; i < n; i++) {
                    if (Math.abs(soln2[i] - soln[i]) / Math.abs(soln2[i]) > 0.00000001) {
                        weightedDone = false;
                        break;
                    }
                }
            } else if (!doneBeforeBounded) {
                done = true;
                doneBeforeBounded = true;
                outer:
                for (int i = 0; i < n; i++) {
                    for (int it = 0; it < numObj; it++) {
                        if (Math.abs(psoln2[it][i] - psoln[it][i]) / Math.abs(psoln2[it][i]) > 0.00000001) {
                            done = false;
                            doneBeforeBounded = false;
                            break outer;
                        }
                    }
                }
            }

            // prepare for next iteration
            tmpsoln = soln;
            soln = soln2;
            soln2 = tmpsoln;

            for (int it = 0; it < numObj; it++) {
                tmpsoln = psoln[it];
                psoln[it] = psoln2[it];
                psoln2[it] = tmpsoln;
            }
        }
        
        for (int it = 0; it < numObj ;it++) {
            sol.setCoord(it,psoln[it][start_index]);
        }

        return sol;
    }



    public MDP getModel() {
        return model;
    }

    public void setModel(MDP model) {
        this.model = model;
    }

    public List<MDPRewards> getRewards() {
        return rewards;
    }

    public void setRewards(List<MDPRewards> rewards) {
        this.rewards = rewards;
    }
}
