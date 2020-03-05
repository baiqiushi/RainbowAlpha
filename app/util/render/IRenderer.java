package util.render;

import model.Point;

public interface IRenderer {

    byte[] createRendering(int _resolution);

    boolean render(byte[] background, double _cX, double _cY, double _halfDimension, int _resolution, Point point);
}
