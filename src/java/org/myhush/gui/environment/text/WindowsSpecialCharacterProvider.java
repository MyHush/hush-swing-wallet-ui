package org.myhush.gui.environment.text;

public class WindowsSpecialCharacterProvider extends SpecialCharacterProvider {
    @Override
    public String getConfirmedBalanceSymbol() {
        return "\u25B7";
    }

    @Override
    public String getUnconfirmedBalanceSymbol() {
        return "\u25B6";
    }

    @Override
    public String getUnlistedAddressSymbol() {
        return "\u25B6";
    }

    @Override
    public String getConnectionSymbol() {
        return "\u21D4";
    }

    @Override
    public String getTickSymbol() {
        return "\u2606";
    }
}
