Gok!llo - More Crowdfunding for Less
====================================

Gok!llo is a Web platform designed to let people publish their projects and get funded by other people or organizations. Originators just need to describe their projects, quantify the funding target, and specify how backers would be rewarded &mdash; that could be a t-shirt, a special edition of the final product, or whatever else. If the target amount has been reached when the fundraising period is over, then the project gets funded, otherwise collected funds are returned to respective backers.

Gok!llo uses Bitcoin as the payment methods, but to protect incoming funds from Bitcoin's volatility they are converted to the currency of the project and held in a bank account. Finally, when the fundraising period ends, funds are converted back to Bitcoin and sent to target recipients.

## Prerequisites

Gok!llo requires the following software to be present on the target system:

* [JDK 8 or later](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Typesafe Activator](https://www.playframework.com)
* [Redis Server](http://redis.io)
* [MongoDB](http://www.mongodb.com)

## Configuration

Configuration items like api keys, account names, or passwords are confidential and must never appear in plain text. Instead, configuration files should reference environment variables that hold the actual values. To set the environment variables required by Gok!llo, run the following script:
```
#!/bin/sh
# CORS_ALLOWED_ORIGINS="https://hostname1,https://hostname2"; export CORS_ALLOWED_ORIGINS
# MONGODB_URI="mongodb://hostname:27017/gokillo"; export MONGODB_URI
# REDISCLOUD_URL="https://rediscloud:password@hostname:port"; export REDISCLOUD_URL
# PAY_MESSAGE_BROKER_URL="nio://api.gokillo.com:61616"; export PAY_MESSAGE_BROKER_URL
# SMTP_HOST="smtp.gokillo.com"; export SMTP_HOST
# SMTP_PORT=587; export SMTP_PORT
# SMTP_SSL=false; export SMTP_SSL
# SMTP_TLS=true; export SMTP_TLS
APP_SECRET="9R0:bhOtusx903GaNr8/Yac^dYYOkAdiT`36Idf^Bn@=7nLsiFlC]BguT<2GVnkn"; export APP_SECRET
SMTP_USER="majordomo@gokillo.com"; export SMTP_USER
SMTP_PASSWORD="Rp2xmgWQUL8V"; export SMTP_PASSWORD
XCHANGE_CLIENT_ID="123456"; export XCHANGE_CLIENT_ID
XCHANGE_BANK_ID="99"; export XCHANGE_BANK_ID
XCHANGE_API_KEY="e0aaffa3bebbf4ecc361af73f7bb901684edb87b32a7f0871ee35120eb751e77"; export XCHANGE_API_KEY
XCHANGE_API_SECRET="f5851ca5811d0161955061408c746c82a40d1d04f3d3d70c05531aaaa9ac1188"; export XCHANGE_API_SECRET
OPS_URL="https://api.sandbox.paypal.com"; export OPS_URL
OPS_CLIENT_ID="AefvWTsejGKFs6kj5STML6D3Xw7Q_i_1cf-y7haV61YN3qC034K-QSqIo4z8ck8SnwPN_X2fEt-IZcH8"; export OPS_CLIENT_ID
OPS_CLIENT_SECRET="EAt2caU79P3zrimkKJmNdlynqjo92260UdC4WrBvABfFoJvKHmqnvUibFV20DnImmpR8dz7VjXSXc3Od"; export OPS_CLIENT_SECRET
GOKILLO_API_URL="https://api.gokillo.com"; export GOKILLO_API_URL
GOKILLO_APIDOCS_URL="https://dev.gokillo.com/api-docs"; export GOKILLO_APIDOCS_URL
GOKILLO_ASSETS_URL="https://assets.gokillo.com"; export GOKILLO_ASSETS_URL
GOKILLO_UI_URL="https://gokillo.com"; export GOKILLO_UI_URL
```
Most environment variables are only required on production and thus they can be commented out on development.

## Building Gok!llo

To build Gok!llo start the `activator` and issue the `compile` command:
```
activator
[gokillo] $ compile

...
[success] Total time: 1 s, completed Nov 5, 2015 9:34:53 PM
```
Then, to run Gok!llo just issue the `run` command:
```
activator
[gokillo] $ compile

--- (Running the application, auto-reloading is enabled) ---

[info] play - Listening for HTTP on /0:0:0:0:0:0:0:0:9000

(Server started, use Ctrl+D to stop and go back to the console...)
```
Finally, to create the distribution package issue the `dist` command:
```
[gokillo] $ dist

...
[success] Total time: 12 s, completed Nov 5, 2015 9:29:47 PM
```
The distribution zip archive can be found in `target/universal`.

## Running Gok!llo

To run Gok!llo in production, move to the top directory of the `distro` and issue the following command (replace `PASSWORD` with the actual password):
```
[j3d@api ~]$ cd /opt/gokillo-api-1.0
[j3d@api gokillo-api-1.0]$ ./bin/gokillo-api -Dhttps.port=9443 -Dhttps.keyStore=/software/certificate/gokillo.com/keystore/gokillo.com.p12 -Dhttps.keyStorePassword=PASSWORD &
```

## License

No part of this software may be reproduced or transmitted in any form or by any means without the prior consent of Gok!llo GmbH.

&copy; 2015 Gok!llo GmbH
