package org.openankus.optimizer.ga;
import java.io.BufferedReader;
import java.io.FileReader;

import org.openankus.optimizer.ml.Algorithm;
import org.openankus.optimizer.ml.DecisionTreeC45;
import org.openankus.optimizer.ml.MLP;
import org.openankus.optimizer.ml.Model;
import org.openankus.optimizer.ml.Parameter;

import weka.core.Instances;

public class GAMain {

	public static void main(String[] args) throws Exception {
		
		// GA ��ü ����
		CGA ga = new CGA();
		
		// GA ȯ�溯�� ����
		int 	seed 			= 1;
		int 	popSize			= 10;
		int		maxGeneration 	= 3;
		float 	crossProb 		= 0.9f;
		float	mutProb			= 0.5f;
		int		binaryStrSize	= 5;	// �������ڿ� ũ��
		
		ga.setParameters(popSize,seed,crossProb,mutProb);
		
		// �ʱ� ��ü���� ����
		int 	numAttri		= 5;		// �Է¼Ӽ� ����	
		int		classIndex		= 4;		// �Էµ����Ϳ� ���� Ŭ���� �Ӽ� ����
		
		// weka �Էµ����� �ҷ��� (arff ����)
		BufferedReader reader = new BufferedReader(new FileReader("D:/Programs/data/iris.arff"));
		Instances data = new Instances(reader);
		data.setClassIndex(classIndex);
		
		Model model = new Model(data);
		Algorithm 	algorithm = null;
		int			numAlgPara = -1;
		Parameter[] parameters = null;
		
		switch("MLP"){
		case "C45":
			algorithm = new DecisionTreeC45();
			numAlgPara = 2;
			parameters = new Parameter[numAlgPara];
			parameters[0] = new Parameter("CF",0.1f,1.0f);
			parameters[1] = new Parameter("min",2.0f,80.0f);
			break;
		case "MLP":
			algorithm = new MLP();
			numAlgPara = 3;
			parameters = new Parameter[numAlgPara];
			parameters[0] = new Parameter("lr",0.1f,1.0f);
			parameters[1] = new Parameter("mm",0.1f,1.0f);
			parameters[2] = new Parameter("h",1.0f,50.0f);
			break;
		};
		
		
		if(numAlgPara != -1){
			// ��ü�ʱ�ȭ
			ga.setInitialPopulation(numAttri,numAlgPara,binaryStrSize,classIndex);
	//		System.out.println("��ü �ʱ�ȭ �Ϸ�...");
			
			// ��ü��	
			ga.evaluation(model,algorithm,parameters);
			
			int generation = 0;
			System.out.println(" ************************************ "+generation+" ����: "+ga.getelitist().getFitness());
			ga.getelitist().toStringModel();
			
			do{
				// ��ü����
				ga.selectMethod();
	//			System.out.println("��ü ���� �Ϸ�..");
			
				// ���� Ȯ��
				ga.crossover();
	//			System.out.println("��ü ���� �Ϸ�..");
			
				// �������� Ȯ��
				ga.mutation(classIndex);
	//			System.out.println("��ü �������� �Ϸ�..");
				
				// ��ü��	
				ga.evaluation(model,algorithm,parameters);			
				
				generation++;
				
				System.out.println(" ************************************ "+generation+" ����: "+ga.getelitist().getFitness());
				ga.getelitist().toStringModel();
	
			}while(generation <= maxGeneration);
		}else{
			System.out.println("���� 00001");
		}
		
		
	}

}
