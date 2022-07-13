for K in 10; do
for prop in 3 4; do

#echo "logs/pred/usefull_states/coin/coin_"$K"_prop"$prop"_uf.log" > addr.txt
#./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -noprob1 -maxiters 9000500 -s -prop $prop -const K=$K -mod3 > logs/compare/state_based/coin/coin_4_$K\_prop$prop\_usefull_states.log

echo "logs/pred/optimal_policies/coin/coin_"$K"_prop"$prop"_op.log" > addr.txt
./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -noprob1 -maxiters 9000500 -s -prop $prop -const K=$K -mod5 #> logs/compare/policy_based/coin/coin_4_$K\_prop$prop\_policy_based.log

#./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -noprob1 -maxiters 9000500 -s -prop $prop -const K=$K > logs/compare/standard/coin/coin_4_$K\_prop$prop\.log

#./bin/prism ../prism-examples/mdps/consensus/coin4.nm -s -const K=$K  -exportstates logs/states/coin/coin_4_$K\.sta

done
done

exit

for K in 4 5 6 8 7 9; do

./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -s -prop 4 -const K=$K -noprob1 -mod1 -maxiters 900050 > logs/uf_states/coin/coin_4_$K\_pmax_uf.log

./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -s -prop 3 -const K=$K -noprob1 -mod1 -maxiters 900050 > logs/uf_states/coin/coin_4_$K\_pmin_uf.log

#./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -s -prop 4 -const K=$K -noprob1 -mod2 > logs/Opt_policies/coin/coin_4_$K\_pmax_op.log

#./bin/prism ../prism-examples/mdps/consensus/coin4.nm ../prism-examples/mdps/consensus/coin.pctl -s -prop 3 -const K=$K -noprob1 -mod2 > logs/Opt_policies/coin/coin_4_$K\_pmin_op.log

done

exit

