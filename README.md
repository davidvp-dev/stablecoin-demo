# stablecoin-demo

Instrucciones para clonar en local y ponerlo en marcha

1. git clone --recursive

forge install añade las librerías (forge-std, OpenZeppelin…) como submódulos de git, y en un monorepo se registran en el .gitmodules de la raíz.

2. Dentro de la carpeta de contracts hacer ln -s ../.env .env
Así contracts/.env “es” el mismo fichero que el de la raíz. Si editas uno, cambia el otro, y Foundry lo encuentra sin tocar nada más

_______________________

Soluciones reales que no estan en el alcance de este proyecto pero que merece la pena mencionar:

1. Para evitar el griefing en el redeem, core-api firma con EIP-712 un mensaje del tipo “el usuario U puede redimir la cantidad N con la orden X antes de la fecha T”. El contrato verifica que la firma es de una dirección con un rol autorizado (por ejemplo, un REDEEM_SIGNER_ROLE) antes de quemar.

2. ...

_______________________

Problemas técnicos que se han ido solucionando

1. El despliegue fallaba porque las herramientas simulaban con reglas de gas anteriores al hard fork de la testnet, y lo diagnostiqué comparando la simulación local con eth_estimateGas de la red.