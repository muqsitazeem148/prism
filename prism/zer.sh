for K in 6 10 14 18; do
for prop in 1 2; do

echo "logs/pred/usefull_states/zeroconf/zer_"$K"_porp"$prop"_uf.log" > addr.txt

./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm ../prism-examples/mdps/zeroconf/zeroconf.pctl -prop $prop -const N=1500,K=$K,reset=false,err=.1 -s -noprob1 -mod3 > logs/compare/state_based/zeroconf/zer_$K\_prop$prop\_state_based.log

echo "logs/pred/optimal_policies/zeroconf/zer_"$K"_porp"$prop"_op.log" > addr.txt

./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm ../prism-examples/mdps/zeroconf/zeroconf.pctl -prop $prop -const N=1500,K=$K,reset=false,err=.1 -s -noprob1 -mod5 > logs/compare/policy_based/zeroconf/zer_$K\_prop$prop\_policy_based.log

./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm ../prism-examples/mdps/zeroconf/zeroconf.pctl -prop $prop -const N=1500,K=$K,reset=false,err=.1 -s > logs/compare/standard/zeroconf/zer_$K\_prop$prop\.log

done
done


exit

for K in 2 3 4; do
for prop in 1 2; do

./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm ../prism-examples/mdps/zeroconf/zeroconf.pctl -prop $prop -const N=1500,K=$K,reset=false,err=.1 -s -noprob1 -mod1 > logs/uf_states/zeroconf/zer_$K\_prop$prop\_uf.log

./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm ../prism-examples/mdps/zeroconf/zeroconf.pctl -prop $prop -const N=1500,K=$K,reset=false,err=.1 -s -noprob1 -mod2 > logs/Opt_policies/zeroconf/zer_$K\_prop$prop\_op.log

done
done

for K in 6 8 10 12 14 16 18; do
./bin/prism ../prism-examples/mdps/zeroconf/zeroconf.nm -const N=1500,K=$K,reset=false,err=.1 -s -exportstates logs/states/zer_$K\.sta
done
