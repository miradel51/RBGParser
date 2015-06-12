package parser;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import parser.Options.LearningMode;

import utils.SVD;
import utils.Utils;

public class LowRankParam implements Comparator<Integer> {
	
	public static boolean averageNorm = false;
	
	public int N, M, D, maxRank;
	public TIntArrayList xlis, ylis, zlis;
	public TDoubleArrayList values;
	private double[][] U, V, W;
	
	public LowRankParam(Parameters parameters) {
		N = parameters.N;
		M = parameters.M;
		if (parameters.options.learnLabel)
			D = parameters.D + parameters.T;
		else D = parameters.D;
		maxRank = parameters.U.length;
		U = new double[maxRank][N];		
		V = new double[maxRank][M];
		W = new double[maxRank][D];
		xlis = new TIntArrayList();
		ylis = new TIntArrayList();
		zlis = new TIntArrayList();
		values = new TDoubleArrayList();
	}
	
	public void putEntry(int x, int y , int z, double value) {
		Utils.Assert(x >= 0 && x < N);
		Utils.Assert(y >= 0 && y < M);
		Utils.Assert(z >= 0 && z < D);
		xlis.add(x);
		ylis.add(y);
		zlis.add(z);
		values.add(value);
	}
	
	public void decompose(int mode, Parameters params) {
		
		int nRows = 0, nCols = 0;
		nRows = N;
		nCols = M * D;
		
		int K = xlis.size();		
		Integer[] lis = new Integer[K];
		for (int i = 0; i < K; ++i) lis[i] = i;
		Arrays.sort(lis, this);
		
		int[] x = new int[K], y = new int[K];
		double[] z = new double[K];
		for (int i = 0; i < K; ++i) {
			int j = lis[i];
			x[i] = xlis.get(j);
			y[i] = ylis.get(j)*D + zlis.get(j);
			z[i] = values.get(j);
		}
		
		double ratio = (K+0.0)/nRows/nCols;
		System.out.printf("  Unfolding matrix: %d / (%d*%d)  %.2f%% entries.%n",
				K, nRows, nCols, ratio*100);
		
		double[] S = new double[maxRank];
		double[] Ut = new double[maxRank*nRows];
		double[] Vt = new double[maxRank*nCols];
		int rank = SVD.svd(nRows, nCols, maxRank, x, y, z, S, Ut, Vt);
		System.out.printf("  Rank: %d (max:%d)  Sigma: max=%f cut=%f%n",
				rank, maxRank, S[0], S[rank-1]);
		
		for (int i = 0; i < rank; ++i) {
			for (int j = 0; j < N; ++j)
				U[i][j] = Ut[i*N+j];

			
			double[] A2 = new double[nCols];
			for (int j = 0; j < nCols; ++j)
				A2[j] = Vt[i*nCols+j];
			Utils.Assert(nCols == M * D);
			
			double[] S2 = new double[1];
			double[] Ut2 = new double[M];
			double[] Vt2 = new double[D];
			int rank2 = SVD.svd(A2, M, D, S2, Ut2, Vt2);
			//System.out.println(rank2);
			//Utils.Assert(rank2 == 1);

			for (int j = 0; j < M; ++j)
				V[i][j] = Ut2[j];
			
			for (int j = 0; j < D; ++j)
				W[i][j] = Vt2[j];		    
			
			if (!averageNorm) {				
				// in order to reproduce results on 1st order parsing shown in the paper
				for (int j = 0; j < D; ++j)
					W[i][j] *= S[i] * S2[0];				
			} else {
		        double coeff = Math.pow(S[i]*S2[0], 1.0/3);
		        for (int j = 0; j < N; ++j)
		            U[i][j] *= coeff;
		        for (int j = 0; j < M; ++j)
		            V[i][j] *= coeff;
				for (int j = 0; j < D; ++j)
					W[i][j] *= coeff;//S[i] * S2[0];
			}
		}
		
		for (int i = 0; i < maxRank; ++i) {
			params.U[i] = U[i].clone();
			params.V[i] = V[i].clone();
			if (params.options.learnLabel) {
				for (int j = 0; j < params.D; ++j)
					params.W[i][j] = W[i][j];
				for (int j = params.D; j < params.D+params.T; ++j)
					params.WL[i][j-params.D] = W[i][j];
			}
			else params.W[i] = W[i].clone();
			
			params.totalU[i] = params.U[i].clone();
			params.totalV[i] = params.V[i].clone();
			params.totalW[i] = params.W[i].clone();
			if (params.options.learnLabel)
				params.totalWL[i] = params.WL[i].clone();
		}
	}

	@Override
	public int compare(Integer u, Integer v) {
		int yu = ylis.get(u)*D + zlis.get(u);
		int yv = ylis.get(v)*D + zlis.get(v);
		if (yu != yv)
			return yu - yv;
		else
			return zlis.get(u) - zlis.get(v);
	}
}
