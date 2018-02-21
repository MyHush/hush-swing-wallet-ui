#!/usr/bin/env sh
if [ -x jre/bin/java ]; then
    JAVA=./jre/bin/java
else
    JAVA=java
fi
${JAVA} -jar HUSHSwingWalletUI.jar org.myhush.gui.App
