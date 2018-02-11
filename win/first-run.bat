rem this script MUST be run before launching hushd for the first time
mkdir %AppData%\Hush
mkdir %LocalAppData%\HushSwingWalletUI

@echo off

IF NOT EXIST %AppData%\Hush\hush.conf (
  (
    echo addnode=explorer.myhush.org
    echo addnode=stilgar.leto.net
    echo addnode=zdash.suprnova.cc
    echo addnode=dnsseed.myhush.org
    echo rpcuser=username
    echo rpcpassword=password%random%%random%
    echo daemon=1
    echo showmetrics=0
    echo gen=0
  ) > %AppData%\Hush\hush.conf
) 

IF NOT EXIST %LocalAppData%\HushSwingWalletUI\addressBook.csv (
  (
    echo t1Npak5Tdb7CZpWS6dzokCPv8ugy7LanJ9g,HUSH Team Donation address
  ) > %LocalAppData%\HushSwingWalletUI\addressBook.csv
)
