package com.spectrum255.meteorosprotocol.particle;

public final class MassBalance {
    private MassBalance() {}
    public static void require(long input,long output,long waste){if(input<0||output<0||waste<0||input!=output+waste) throw new IllegalArgumentException("mass balance: "+input+" != "+output+" + "+waste);}
    public static long loss(long input,long output){if(input<output) throw new IllegalArgumentException("output exceeds input"); return input-output;}
}
