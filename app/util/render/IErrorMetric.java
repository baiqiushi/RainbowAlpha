package util.render;

public interface IErrorMetric {
    double totalError(byte[] _rendering1, byte[] _rendering2, int _resolution);
}
