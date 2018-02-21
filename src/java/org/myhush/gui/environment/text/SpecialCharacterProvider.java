package org.myhush.gui.environment.text;

// BRX-TODO: It's nonsense to need this class, let's support a font across the entire app on all platforms
// BRX-TODO: that actually has the characters we want?
public abstract class SpecialCharacterProvider {
    public abstract String getConfirmedBalanceSymbol();
    public abstract String getUnconfirmedBalanceSymbol();
    public abstract String getUnlistedAddressSymbol();
    public abstract String getConnectionSymbol();
    public abstract String getTickSymbol();
}
