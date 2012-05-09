
package com.smit.pictureshow;

     class CoordParameter {
    	 
	public int cellX  = -1;
	public int cellY  = -1;
	public int screen = -1;

	CoordParameter() {
	}

	CoordParameter(CoordParameter info) {
		cellX  = info.cellX;
		cellY  = info.cellY;
		screen = info.screen;
	}
}