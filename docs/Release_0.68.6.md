This is a [HUSH](https://myhush.org/) Desktop GUI Wallet binary release 0.68.6 for Windows. 
It requires a 64-bit Windows 7 or later version to run. It also includes 
[HUSH 1.0.9 binaries](https://zcash.dl.mercerweiss.com/hush-win-v1.0.9.zip) by 
[@radix42](https://github.com/radix42). 

### Installing the HUSH Desktop GUI Wallet on Windows

1. Download the Wallet ZIP file 
[HUSH_Wallet_0.68.6_Windows_release.zip](https://github.com/vaklinov/hush-swing-wallet-ui/releases/download/0.68.6/HUSH_Wallet_0.68.6_Windows_release.zip). 

2. Security check: You may decide to run a virus scan on it, before proceeding... In addition using a tool 
such as [http://quickhash-gui.org/](http://quickhash-gui.org/) you may calculate the its SHA256 checksum. The 
result should be:
```
c633a6e4067e7bb7a9438c93621b8a2cfb5b5076ac2cda3401244128586a925e  HUSH_Wallet_0.68.6_Windows_release.zip
```
**If the resulting checksum is not `c633a6e4067e7bb7a9438c93621b8a2cfb5b5076ac2cda3401244128586a925e` then**
**something is wrong and you should discard the downloaded wallet!**

3. Unzip the Wallet ZIP file HUSH_Wallet_0.68.6_Windows_release.zip in some directory that it will run from.
   
### Running the HUSH Desktop GUI Wallet on Windows

Double click on `HUSHSwingWalletUI.exe`. On first run (only) the wallet will download the cryptographic keys 
(900MB or so). In case of problems logs are written in `%LOCALAPPDATA%\HUSHSwingWalletUI\` for diagnostics.

### Disclaimer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### Known issues and limitations
1. In rare cases users have [reported problems](https://github.com/vaklinov/hush-swing-wallet-ui/issues/1)
when running the GUI wallet using certain ATI video drivers/cards. If such a problem is encountered then a 
user may run `HUSHSwingWalletUI.jar` instead of `HUSHSwingWalletUI.exe`. This JAR file will be runnable 
only if there is a Java JDK installed separately on the system. To install JDK 8 for Windows you may use 
this [good tutorial](http://www.wikihow.com/Install-the-Java-Software-Development-Kit)
1. Issue: GUI data tables (transactions/addresses etc.) allow copying of data via double click but also allow editing. 
The latter needs to be disabled. 
1. Limitation: The list of transactions does not show all outgoing ones (specifically outgoing Z address 
transactions).  
