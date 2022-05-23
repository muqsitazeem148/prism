for dl in 4; do
for ddl in 5000 7500 10000; do

echo "logs/pred/usefull_states/firewire/firewire_uf_"$dl"_"$ddl".log" > addr.txt

./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm ../prism-examples/mdps/firewire/abst/deadline.pctl -s -const delay=$dl,deadline=$ddl,fast=.1 -noprob1 -mod3 > logs/compare/state_based/firewire/firewire_$dl\_$ddl\_usefull_states.log


echo "logs/pred/optimal_policies/firewire/firewire_op_"$dl"_"$ddl".log" > addr.txt

./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm ../prism-examples/mdps/firewire/abst/deadline.pctl -s -const delay=$dl,deadline=$ddl,fast=.1 -noprob1 -mod4 > logs/compare/policy_based/firewire/firewire_$dl\_$ddl\_policy_based.log

./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm ../prism-examples/mdps/firewire/abst/deadline.pctl -s -const delay=$dl,deadline=$ddl,fast=.1 > logs/compare/standard/firewire/firewire_$dl\_$ddl\.log

done
done

exit


# Scripts for generating sample data for training:

for dl in 4 24; do
for ddl in 800 1000 1200; do

./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm ../prism-examples/mdps/firewire/abst/deadline.pctl -s -const delay=$dl,deadline=$ddl,fast=.1 -noprob1 -mod1 > logs/uf_states/firewire/firewire_$dl\_$ddl\_uf.log

./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm ../prism-examples/mdps/firewire/abst/deadline.pctl -s -const delay=$dl,deadline=$ddl,fast=.1 -noprob1 -mod2 > logs/Opt_policies/firewire/firewire_$dl\_$ddl\_op.log

#./bin/prism ../prism-examples/mdps/firewire/abst/deadline.nm -s -const delay=$dl,deadline=$ddl,fast=.12 -exportstates logs/states/firewire_$dl\_$ddl\.sta 

done
done

