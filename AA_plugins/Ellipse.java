package AA_plugins;

import java.util.*;
import Jama.*;
//import org.doube.geometry.FitEllipsoid; import Geometry.*;
import static java.lang.Math.atan2;
import static java.lang.Math.hypot;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;

public class Ellipse {
	public ClusterChain chain;
	public double radiusA;
	public double radiusB;
	public double radiusC;
	public double angleDegA;
	public double angleDegB;
	public double angleDegC;
	public double[] centre;

	public Ellipse(ClusterChain chain) {
		this.chain = chain;

		this.findInertiaEllipsoid();
		





		/*double[][] points = new double[pointsList.size()][3];
		int i = 0;
		for (Point p: pointsList) {
			points[i][0] = p.x;
			points[i][1] = p.y;
			points[i][2] = p.cluster.z;
			i++;
		}
		//System.out.println("for chain " + chain + "points length was " + points.length);
		Object[] ellipseStats = Ellipse.yuryPetrov(points);
		double[] centre = (double[]) ellipseStats[0];
		double[] radii = (double[]) ellipseStats[1];
		double[][] eigenVectors = (double[][]) ellipseStats[2];
		double[] equation = (double[]) ellipseStats[3];
		
		//System.out.println("Chain with first cluster " + chain.clusters.get(0));
		//System.out.println("Centre: " + centre[0] +","+ centre[1] + ","+ centre[2]);
		//System.out.println("Radii: " + radii[0] + ", " + radii[1] + ","+ radii[2]);
		if (Math.abs(centre[0]) >= 0 && Math.abs(centre[0]) <= 650) {
			System.out.println("Chain with first cluster " + chain.clusters.get(0));
			System.out.println("Centre: " + centre[0] +","+ centre[1] + ","+ centre[2]);
			System.out.println("Radii: " + radii[0] + ", " + radii[1] + ","+ radii[2]);
			String equationText = "Equation is " + equation[0] + "X^2 + " + equation[1] + "Y^2 + " + equation[2] + "Z^2 + 2*";
			equationText = equationText+ equation[3] + "xy + 2*" + equation[4] + "xz + 2*" + equation[5] + "yz + 2*";
			equationText = equationText + equation[6] + "x + 2*" + equation[7] + "y + 2*" + equation[8] + "z = 1";
			System.out.println(equationText);
			for (Cluster c: chain.clusters) {
				System.out.println(c);
			}
		}*/
	}

	public double getMajorMinorRatio() {
		if (radiusA >= radiusB && radiusA >= radiusC) {
			return Math.min(radiusA/radiusB, radiusA/radiusC);
		}
		else if (radiusB > radiusA && radiusB >= radiusC) {
			return Math.min(radiusB/radiusA, radiusB/radiusC);
		}
		else {
			//radiusC > radiusA && radiusC > radiusB
			return Math.min(radiusC/radiusA, radiusC/radiusB);
		}

	}
	
	/*
	//method from org.doube.geometry.FitEllipsoid.java [BoneJ] - https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry/FitEllipsoid.java
	//I couldn't find which package this class would be in...
	public static Object[] yuryPetrov(final double[][] points) {

		final int nPoints = points.length;
		if (nPoints < 9) {
			throw new IllegalArgumentException("Too few points; need at least 9 to calculate a unique ellipsoid");
		}

		final double[][] d = new double[nPoints][9];
		for (int i = 0; i < nPoints; i++) {
			final double x = points[i][0];
			final double y = points[i][1];
			final double z = points[i][2];
			d[i][0] = x * x;
			d[i][1] = y * y;
			d[i][2] = z * z;
			d[i][3] = 2 * x * y;
			d[i][4] = 2 * x * z;
			d[i][5] = 2 * y * z;
			d[i][6] = 2 * x;
			d[i][7] = 2 * y;
			d[i][8] = 2 * z;
		}

		// do the fitting
		final Matrix D = new Matrix(d);
		final Matrix ones = Ellipse.ones(nPoints, 1);
		final Matrix V = ((D.transpose().times(D)).inverse()).times(D.transpose().times(ones));

		// the fitted equation
		final double[] v = V.getColumnPackedCopy();

		final Object[] matrices = Ellipse.matrixFromEquation(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);

		// pack data up for returning
		final EigenvalueDecomposition E = (EigenvalueDecomposition) matrices[3];
		final Matrix eVal = E.getD();
		final Matrix diagonal = Ellipse.diag(eVal);
		final int nEvals = diagonal.getRowDimension();
		final double[] radii = new double[nEvals];
		for (int i = 0; i < nEvals; i++) {
			radii[i] = Math.sqrt(1 / diagonal.get(i, 0));
		}
		final double[] centre = (double[]) matrices[0];
		final double[][] eigenVectors = (double[][]) matrices[2];
		final double[] equation = v;
		final Object[] ellipsoid = { centre, radii, eigenVectors, equation, E };
		return ellipsoid;
	}
	
	//from https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry/Ellipsoid.java [BoneJ] -- same deal as above.
	public static Object[] matrixFromEquation(final double a, final double b, final double c, final double d,
			final double e, final double f, final double g, final double h, final double i) {

		// the fitted equation
		final double[][] v = { { a }, { b }, { c }, { d }, { e }, { f }, { g }, { h }, { i } };
		final Matrix V = new Matrix(v);

		// 4x4 based on equation variables
		final double[][] aa = { { a, d, e, g }, { d, b, f, h }, { e, f, c, i }, { g, h, i, -1 }, };
		final Matrix A = new Matrix(aa);

		// find the centre
		final Matrix C = (A.getMatrix(0, 2, 0, 2).times(-1).inverse()).times(V.getMatrix(6, 8, 0, 0));

		// using the centre and 4x4 calculate the
		// eigendecomposition
		final Matrix T = Ellipse.eye(4);
		T.setMatrix(3, 3, 0, 2, C.transpose());
		final Matrix R = T.times(A.times(T.transpose()));
		final double r33 = R.get(3, 3);
		final Matrix R02 = R.getMatrix(0, 2, 0, 2);
		final EigenvalueDecomposition E = new EigenvalueDecomposition(R02.times(-1 / r33));

		final double[] centre = C.getColumnPackedCopy();
		final double[][] eigenVectors = E.getV().getArrayCopy();
		final double[][] eigenValues = E.getD().getArrayCopy();
		final Object[] result = { centre, eigenValues, eigenVectors, E };
		return result;
	}
	
	// source BoneJ: https://github.com/mdoube/BoneJ/blob/master/src/org/doube/jama/Matrix.java
	public static Matrix ones(final int m, final int n) {
		final double[][] ones = new double[m][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				ones[i][j] = 1;
			}
		}
		return new Matrix(ones);
	}
	
	//source https://github.com/mdoube/BoneJ/blob/master/src/org/doube/jama/Matrix.java
	public static Matrix eye(final int n) {
		return Ellipse.eye(n, n);
	}
	
	//source https://github.com/mdoube/BoneJ/blob/master/src/org/doube/jama/Matrix.java
	public static Matrix eye(final int m, final int n) {
		final double[][] eye = new double[m][n];
		final int min = Math.min(m, n);
		for (int i = 0; i < min; i++) {
			eye[i][i] = 1;
		}
		return new Matrix(eye);
	}
	
	//method modified from https://github.com/mdoube/BoneJ/blob/master/src/org/doube/jama/Matrix.java
	public static Matrix diag(Matrix matrix) {
		int m = matrix.getRowDimension();
		int n = matrix.getColumnDimension();
		final int min = Math.min(m, n);
		final double[][] diag = new double[min][1];
		for (int i = 0; i < min; i++) {
			diag[i][0] = matrix.get(i, i);
		}
		return new Matrix(diag);
	}*/

	//method modified from https://github.com/ijpb/MorphoLibJ/blob/master/src/main/java/inra/ijpb/measure/GeometricMeasures3D.java
	public void findInertiaEllipsoid() {
		ArrayList<Point> pointsList = this.chain.getPointsList();

		//results:
		int count = pointsList.size();
		double constX = 0;
		double constY = 0;
		double constZ = 0;
		double constXX = 0;
		double constYY = 0;
		double constZZ = 0;
		double constXZ = 0;
		double constXY = 0;
		double constYZ = 0;

		//find the centroid of region:
		for (Point p: pointsList) {
			constX += p.x;
			constY += p.y;
			constZ += p.cluster.z;
		}
		constX /= count;
		constY /= count;
		constZ /= count;

		//compute centered inertia matrix:
		for (Point p: pointsList) {
			//first- find relative coordinates to centroid of region:
			double x = ((double) p.x) - constX;
			double y = ((double) p.y) - constY;
			double z = ((double) p.cluster.z) - constZ;

			constXX += x*x;
			constYY += y*y;
			constZZ += z*z;
			constXY += x*y;
			constXZ += x*z;
			constYZ += y*z;
		}
		constXX /= count;
		constYY /= count;
		constZZ /= count;
		constXY /= count;
		constXZ /= count;
		constYZ /= count;

		//double[] res = new double[9];

		//This is the equation of the ellipsoid (created through 'inertia') -- 
		// that formula actually makes a lot of sense.

		//next: compute some result parameters:
		Matrix matrix = new Matrix(3,3);
		matrix.set(0, 0, constXX);
		matrix.set(0, 1, constXY);
		matrix.set(0, 2, constXZ);
		matrix.set(1, 0, constXY);
		matrix.set(1, 1, constYY);
		matrix.set(1, 2, constYZ);
		matrix.set(2, 0, constXZ);
		matrix.set(2, 1, constYZ);
		matrix.set(2, 2, constZZ);

		//find the singular values of this matrix:
		SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
		Matrix values = svd.getS();

		//find radii:
		double r1 = sqrt(5) * sqrt(values.get(0,0));
		double r2 = sqrt(5) * sqrt(values.get(1,1));
		double r3 = sqrt(5) * sqrt(values.get(2,2));
		//I don't know why sqrt(5) is used....

		// extract |cos(theta)| 
		Matrix mat = svd.getU();
		double tmp = hypot(mat.get(1, 1), mat.get(2, 1));
		double angle1, angle2, angle3;

		// avoid dividing by 0
		if (tmp > 16 * Double.MIN_VALUE) 
		{
			// normal case: theta <> 0
			angle3   = atan2( mat.get(2, 1), mat.get(2, 2));
			angle2   = atan2(-mat.get(2, 0), tmp);
			angle1   = atan2( mat.get(1, 0), mat.get(0, 0));
		}
		else 
		{
			// theta is around 0 
			angle3   = atan2(-mat.get(1, 2), mat.get(1,1));
			angle2   = atan2(-mat.get(2, 0), tmp);
			angle1   = 0;
		}

		// add coordinates of origin pixel (IJ coordinate system) 
		this.centre = new double[] {constX + .5, constY + .5, constZ + .5};

		this.radiusA = r1;
		this.radiusB = r2;
		this.radiusC = r3;
		this.angleDegA = angle1;
		this.angleDegB = toDegrees(angle2);
		this.angleDegC = toDegrees(angle3);



	}

}

