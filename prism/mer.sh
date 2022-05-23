for n in 100 200 500 750 1000 2000 3500 5000; do

echo "logs/pred/usefull_states/mer/mer_"$n"_uf.log" > addr.txt
./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm ../prism-examples/mdps/mer/mer.pctl -s -prop 1 -const n=$n,x=.1 -noprob1 -mod3 > logs/compare/state_based/mer/mer_$n\_usefull_states.log

echo "logs/pred/optimal_policies/mer/mer_"$n"_op.log" > addr.txt
./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm ../prism-examples/mdps/mer/mer.pctl -s -prop 1 -const n=$n,x=.1 -noprob1 -mod4 > logs/compare/policy_based/mer/mer_$n\_policy_based.log

./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm ../prism-examples/mdps/mer/mer.pctl -s -prop 1 -const n=$n,x=.1 -noprob1 > logs/compare/standard/mer/mer_$n\.log

done

exit

for n in 20 35 50; do

./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm ../prism-examples/mdps/mer/mer.pctl -s -prop 1 -noprob1 -const n=$n,x=.1 -mod1 > logs/uf_states/mer/mer_$n\_uf.log

./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm ../prism-examples/mdps/mer/mer.pctl -s -prop 1 -noprob1 -const n=$n,x=.1 -mod2 > logs/Opt_policies/mer/mer_$n\_op.log

done

for n in 2000 3500 5000; do

./bin/prism ../prism-examples/mdps/mer/mer2_2_full.nm -const n=$n,x=.1 -exportstates logs/states/mer/mer_$n\.sta

done
