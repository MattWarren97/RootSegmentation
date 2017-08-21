package AA_plugins;

import java.util.*;
import Jama.*;
//import org.doube.geometry.FitEllipsoid; import Geometry.*;


public class Ellipse {
	public ClusterChain chain;
	public float majorAxis;
	public float minorAxis;
	public float angleRad;

	public Ellipse(ClusterChain chain) {
		this.chain = chain;
		ArrayList<Point> pointsList = chain.getPointsList();
		double[][] points = new double[pointsList.size()][3];
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
		}
	}

	public float getMajorMinorRatio() {
		return this.majorAxis/this.minorAxis;
	}
	
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
	}

}

