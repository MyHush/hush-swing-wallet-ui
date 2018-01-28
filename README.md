# hush4win

## A Hush full node and GUI wallet for 64-bit Windows

This software package incorporates the port of zcash to Windows by @radix42 and the
ZCashSwingWallet by @vaklinov with enhancements by @zab and @TJ_Trunk



### License
This program is distributed under an [MIT License](https://github.com/vaklinov/zcash-swing-wallet-ui/raw/master/LICENSE).


### Known issues and limitations

1. Issue: Wallet versions 0.58 and below, when running on systems with (typically non-western) locales that
redefine the decimal point in the OS locale settings, have problems with updating the GUI wallet state. 
A workaround is to change the [locale settings](https://windows.lbl.gov/software/optics/5-1-2/Optics4.jpg) to have dot as decimal separator.
1. Limitation: Wallet encryption has been temporarily disabled in all ZCash forks due to stability problems. A corresponding issue 
[#1552](https://github.com/zcash/zcash/issues/1552) has been opened by the ZCash developers. Correspondingly
wallet encryption has been temporarily disabled in the Hush Desktop GUI Wallet.
1. Issue: the GUI wallet does not work correctly if hushd is started with a custom data directory, like:
`hushd -datadir=/home/data/whatever` This will be fixed in later versions.
1. Issue: GUI data tables (transactions/addresses etc.) allow copying of data via double click but also allow editing. 
The latter needs to be disabled. 
1. Limitation: The list of transactions does not show all outgoing ones (specifically outgoing Z address 
transactions). A corresponding issue [#1438](https://github.com/zcash/zcash/issues/1438) has been opened 
for the ZCash developers. 
1. Limitation: The CPU percentage shown to be taken by hushd on Linux is the average for the entire lifetime 
of the process. This is not very useful. This will be improved in future versions.

