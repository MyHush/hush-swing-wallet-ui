package org.myhush.gui.environment.text;

public class NonWindowsSpecialCharacterProvider extends SpecialCharacterProvider {
    @Override
    public String getConfirmedBalanceSymbol() {
        return "\u2690";
    }

    @Override
    public String getUnconfirmedBalanceSymbol() {
        return "\u2691";
    }

    @Override
    public String getUnlistedAddressSymbol() {
        return "\u26D4";
    }

    @Override
    public String getConnectionSymbol() {
        return "\u26D7";
    }

    @Override
    public String getTickSymbol() {
        return "\u2705";
    }
}
