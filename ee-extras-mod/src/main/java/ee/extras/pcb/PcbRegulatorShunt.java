package ee.extras.pcb;

/** Пассивная ВАХ стабилитрона: 5 В, динамическое сопротивление 0,2 Ом; обратный переход 0,7 В. */
public final class PcbRegulatorShunt extends PcbBridgeDiode {
    @Override public void tick(double[] v,int micro,int count,int a,int b) {
        double current,conductance;
        if(lastVoltage>5) {conductance=5;current=(lastVoltage-5)*conductance;}
        else if(lastVoltage<-.7) {conductance=5;current=(lastVoltage+.7)*conductance;}
        else {conductance=1e-9;current=lastVoltage*conductance;}
        resistance=1/conductance;
        currentSource=conductance*lastVoltage-current;
    }
}
