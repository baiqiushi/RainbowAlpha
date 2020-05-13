package util.render;

import model.Point;

public interface IRenderer {

    byte[] createRendering(int _resolution, boolean _expansion);

    boolean render(byte[] background, double _cX, double _cY, double _halfDimension, int _resolution, boolean _expansion, Point point);

    byte[] createFullRendering(int _resolution);
}
