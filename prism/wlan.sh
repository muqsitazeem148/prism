for ttm in 200 500 1000 2000 3500 5000; do


./bin/prism ../prism-examples/mdps/wlan/wlan5.nm ../prism-examples/mdps/wlan/wlan.pctl -s -prop 2 -noprob1 -const k=5,TRANS_TIME_MAX=$ttm > logs/compare/standard/wlan/wlan_5_$ttm\.log

echo "logs/pred/useful_states/wlan/wlan_5_"$ttm"_uf.log" > addr.txt
./bin/prism ../prism-examples/mdps/wlan/wlan5.nm ../prism-examples/mdps/wlan/wlan.pctl -s -prop 2 -noprob1 -const k=5,TRANS_TIME_MAX=$ttm -mod3 > logs/compare/state_based/wlan/wlan_5_$ttm\_useful_states.log

echo "logs/pred/optimal_policies/wlan/wlan_5_"$ttm"_op.log" > addr.txt
./bin/prism ../prism-examples/mdps/wlan/wlan5.nm ../prism-examples/mdps/wlan/wlan.pctl -s -prop 2 -noprob1 -const k=5,TRANS_TIME_MAX=$ttm -mod4 > logs/compare/policy_based/wlan/wlan_5_$ttm\_policy_based.log

done

exit

for ttm in 20 35 60; do

./bin/prism ../prism-examples/mdps/wlan/wlan5.nm ../prism-examples/mdps/wlan/wlan.pctl -s -prop 2 -const k=5,TRANS_TIME_MAX=$ttm -noprob1 -mod1 > logs/uf_states/wlan/wlan_5_$ttm\_uf.log

./bin/prism ../prism-examples/mdps/wlan/wlan5.nm ../prism-examples/mdps/wlan/wlan.pctl -s -prop 2 -const k=5,TRANS_TIME_MAX=$ttm -noprob1 -mod2 > logs/Opt_policies/wlan/wlan_5_$ttm\_op.log

done

for ttm in 2000 3500 5000; do

./bin/prism ../prism-examples/mdps/wlan/wlan5.nm -s -const k=5,TRANS_TIME_MAX=$ttm -exportstates logs/states/wlan/wlan_5_$ttm\.sta

done
