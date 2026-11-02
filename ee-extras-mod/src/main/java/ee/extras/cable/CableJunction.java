package ee.extras.cable;

/**
 * Блок, который не может быть наследником CableEndBlock (у него другой родитель), но принимает кабель так же: узел 0 —
 * точка кабеля, узлы 1…n — жилы, все кабели соединены жила к жиле. Пример — прожектор IE в сети EE (lantern/EeFloodlightBlock).
 */
public interface CableJunction {
    CableKind cableKind();
}
