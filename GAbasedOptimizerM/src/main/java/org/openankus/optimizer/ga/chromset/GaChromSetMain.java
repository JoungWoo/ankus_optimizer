package org.openankus.optimizer.ga.chromset;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.openankus.optimizer.ml.Algorithm;
import org.openankus.optimizer.ml.DecisionTreeC45;
import org.openankus.optimizer.ml.KNN;
import org.openankus.optimizer.ml.MLP;
import org.openankus.optimizer.ml.Parameter;
import org.openankus.optimizer.ml.RForest;

/**
 * 개체집합 단위로 유전자 알고리즘 최적화를 수행하는 Main 클래스
 *
 */
public class GaChromSetMain {
	
	/**
	 * 알고리즘 수행에 필요한 데이터를 담는 최상위 디렉토리
	 */
	public static final String DIR_ROOT = "/ga";
	
	/**
	 * 개체집단 데이터를 담는 디렉토리
	 */
	public static final String DIR_POP = DIR_ROOT+"/pop";
	
	/**
	 * 개체집단 데이터를 담는 디렉토리의 prefix
	 */
	public static final String PREFIX_DIR_GA = DIR_POP+"/ga_";
	
	/**
	 * 예측모델 생성용 입력데이터 파일을 담는 디렉터리
	 */
	public static final String DIR_INPUT_DATA = DIR_ROOT+"/data";
	
	/**
	 * 라이브러리 파일 경로
	 */
	public static final String DIR_LIB = DIR_ROOT+"/lib";
	
	/**
	 * 개체집합 출력파일의 prefix
	 */
	public static final String PREFIX_CHROMSET = "chromSet";

	/**
	 * 개체 출력파일의 prefix
	 */
	public static final String PREFIX_CHROM = "chrom";
	
	
	
	//GA 환경변수 설정
	//cmd 예: -s 1 -p 10 -mG 3 -cp 0.9 -mp 0.5 -bs 5 -in D:/Programs/data/iris.arff
	static int 	seed = 1;
	static int 		popSize = 300;
	static int		maxGeneration = 500;
//	static int 		popSize = 3000;
//	static int		maxGeneration = 2;
	static float 	crossProb = 0.9f;
	static float	mutProb = 0.5f;
	static int		binaryStrSize = 5;	// 이진문자열 크기
//	String  inputFile = "D:/Programs/data/iris.arff";
//	String  inputFile = DIR_INPUT_DATA+"/iris.arff";
	static String  inputFile = DIR_INPUT_DATA+"/nursery_shuffle.arff";
	
	// 초기 개체집단 생성
	static int 	numAttri        = 9;		// 입력속성 개수	
	static int	classIndex		= 8;		// 입력데이터에 대한 클래스 속성 설정
	static String algorithmName = "C45";
	
	// 개체집합 당 개체수
	static int sizeOfSet = 20;
	

	public static void main(String[] args) throws Exception {
		
		// Start time
		long startTime = System.nanoTime();
		         
		// 수행작업
		doParrallelGa(args);

		// End time
		long endTime = System.nanoTime();
		 
		// Total time
		long lTime = endTime - startTime;
//		System.out.println("총 소요시간 : " + lTime/1000000.0 + "(ms)");
		System.out.println("총 소요시간 : " + lTime/1000000.0/1000.0 + "(seconds)");
		
	}
	
	
	/**
	 * 개체집합 병렬분산 GA 실행
	 * 
	 * @param args	시스템 인수
	 */
	public static void doParrallelGa(String[] args) throws Exception{

		int numAlgPara = -1;
		
		switch(algorithmName){
		case "C45":
			System.out.println("C45");
			numAlgPara = 2;
			break;
		case "MLP":
			System.out.println("MLP");			
			numAlgPara = 3;
			break;
		case "KNN":
			System.out.println("KNN");	
			numAlgPara = 1;
			break;
		case "RandomForest":
			System.out.println("RandomForest");	
			numAlgPara = 2;
		};
		
		
		if(numAlgPara != -1){
			
			//-----<개체 초기화 및 개체집합 파일저장>------
			// GA 객체 생성
			GA ga = new GA();
			ga.setParameters(popSize,seed,crossProb,mutProb);
			Chrom[] chromList = ga.setInitialPopulation(numAttri, numAlgPara, binaryStrSize, classIndex);
			String chromSetListFilePath = DIR_POP+"/chromSet.txt"; 
			saveInitChromSetList(chromList, sizeOfSet, chromSetListFilePath);
			System.out.println("개체 초기화 완료...");
			//-----</개체 초기화 및 개체집합 파일저장>------
			

			//	최대 분산병렬 GA 횟수
			int maxParrallelGa = 1;
			
			//	분산병렬 GA 수행 횟수
			int countParallelGa = 0;
			
			//	분산병렬 GA 수행
			while (countParallelGa < maxParrallelGa){
				
				
				//-----<개체집합 별 진화 MapReduce>------
				String outputDirPath = PREFIX_DIR_GA+String.format("%05d", countParallelGa);
				{
					// 경로 체크 및 이전 파일삭제
					Configuration conf = new Configuration();
					FileSystem hdfs = FileSystem.get(conf);
					Path path = new Path(outputDirPath);
					if (hdfs.exists(path)) {
						hdfs.delete(path, true);
					}
					
					// 개체집합 별 진화 MapReduce 실행
					String[] mrArgs = {inputFile, chromSetListFilePath, outputDirPath};
				    int res = ToolRunner.run(new Configuration(), new GaChromSetDrive(), mrArgs);
				    System.out.println("Genetic Algorithm MR-Job Result:" + res);
					
				}
				chromSetListFilePath = outputDirPath+"/"+ PREFIX_CHROMSET + "-r-00000";
				System.out.println("개체집합 출력파일 경로: "+ outputDirPath);
				//-----</개체집합 별 진화 MapReduce>------
				

				
				
				
				//-----<전체 개체들을 적합도 내림차순으로 정렬하는 MapReduce>------
			    String chromListFilePath = outputDirPath+"/" + PREFIX_CHROM+"-r-00000";
			    String outputDirPath2 = PREFIX_DIR_GA+String.format("%05d", countParallelGa)+"_sort";
			    {

					// 경로 체크 및 이전 파일삭제
					Configuration conf = new Configuration();
					FileSystem hdfs = FileSystem.get(conf);
					Path path = new Path(outputDirPath2);
					if (hdfs.exists(path)) {
						hdfs.delete(path, true);
					}
				    
				    // 출력결과 정렬 MapReduce 실행
					String[] args2 = {chromListFilePath, outputDirPath2};
				    int res2 = ToolRunner.run(new Configuration(), new SortTextKeyDrive(), args2);
				    System.out.println("Sorting MR-Job Result:" + res2);
			    	
			    }
				System.out.println("개체목록 출력파일 경로: "+ outputDirPath2);
				//-----</전체 개체들을 적합도 내림차순으로 정렬하는 MapReduce>------
				
			    
				// 분산병렬 GA 수행 횟수
			    countParallelGa++;
				
			}
			
			
		}else{
			System.out.println("오류 00001");
		}
		
	}
	
	
	
	
	/**
	 * 초기 개체목록을 개체집합 형식으로 파일 저장(저장되는 파일명: )
	 * 
	 * @param chromList 개체목록
	 * @param sizeOfSet 개체집합의 크기
	 * @param filePath  저장 파일 경로
	 */
	public static void saveInitChromSetList(Chrom[] chromList, int sizeOfSet, String filePath) throws Exception{
		
		// 파일 시스템 제어 객체 생성
		Configuration conf = new Configuration();
		FileSystem hdfs = FileSystem.get(conf);
		
		// 경로 체크 및 이전 파일삭제
		Path path = new Path(filePath);
		if (hdfs.exists(path)) {
			hdfs.delete(path, true);
		}
		
		// 파일 저장
		FSDataOutputStream outStream = hdfs.create(path);
		int bufferSize = 0;
		StringBuffer sb = null;
		int idxChromSet = 0; //	염색체집합 인덱스
		for (Chrom chrom : chromList){
			if (bufferSize == 0 || bufferSize == sizeOfSet){
				sb = new StringBuffer(); 
				//	염색체집합 ID
				sb.append("ChromSet"+String.format("%04d", idxChromSet++)+"\t");
				bufferSize = 0;
			}
			sb.append(chrom.toStringFitnessGene());
			bufferSize++;
			
			if (bufferSize < sizeOfSet){
				sb.append(Chrom.DELIMITER_CHROM);	 //	개체 구분자
			} else if (bufferSize == sizeOfSet){
				sb.append("\n"); //	개체집합 구분자
				outStream.write((sb.toString()).getBytes());
			}
		}
		if (bufferSize != sizeOfSet){
			sb.append("\n"); //	개체집합 구분자
			outStream.write((sb.toString()).getBytes());
		}
		outStream.close();
	}
	
	
	
	/**
	 * 개체집합 별 진화 MapReduce 수행을 위한 Drive 클래스
	 */
	public static class GaChromSetDrive extends Configured implements Tool{
		
		@Override
		public int run(String[] args) throws Exception 
		{
			
		    // Job 이름 설정
			Configuration conf = getConf();
		    Job job = new Job(conf, "GaChromSetDrive");
		    
		    // Job MapRuduce 실행 파라메터 설정
		    job.getConfiguration().set("mapred.max.split.size", String.valueOf(2048));	// 최대 split size
//		    int numSlaves = 4;	//	Slave(데이터 노드) 개수
//		    int estSplitSize = 60 * popSize / numSlaves;	//	Slave 당 map task가 수행되게하는 예측 최대 split 사이즈
//		    job.getConfiguration().set("mapred.max.split.size", String.valueOf(estSplitSize));	// 최대 split size
//		    job.getConfiguration().set("mapred.min.split.size", String.valueOf(1024));	// 최소 split size
//		    job.getConfiguration().set("mapred.map.tasks", String.valueOf(10000));	// 수행 map task 수
		    
		    // MapReduce에 사용될 파라메터 설정
		    job.getConfiguration().set("GaChromSetMap.seed", String.valueOf(seed));
		    job.getConfiguration().set("GaChromSetMap.popSize", String.valueOf(popSize));
		    job.getConfiguration().set("GaChromSetMap.maxGeneration", String.valueOf(maxGeneration));
		    job.getConfiguration().set("GaChromSetMap.crossProb", String.valueOf(crossProb));
		    job.getConfiguration().set("GaChromSetMap.mutProb", String.valueOf(mutProb));
		    job.getConfiguration().set("GaChromSetMap.binaryStrSize", String.valueOf(binaryStrSize));
		    job.getConfiguration().set("GaChromSetMap.numAttri", String.valueOf(numAttri));
		    job.getConfiguration().set("GaChromSetMap.classIndex", String.valueOf(classIndex));
		    job.getConfiguration().set("GaChromSetMap.algorithmName", String.valueOf(algorithmName));
			
		    
		    
		    //---<Map 및 Reduce에 사용될 로컬캐쉬 파일 등록>----
		    // 학습 데이터  등록
			FileSystem hdfs = FileSystem.get(conf);
			DistributedCache.addCacheFile(new Path(args[0]).toUri(), job.getConfiguration());
			
		    // Machine Learning 알고리즘 관련 jar
			FileStatus[] fileStatuses = hdfs.listStatus(new Path(DIR_LIB));
			for (FileStatus fileStatus : fileStatuses){
				if (!fileStatus.isDir() && fileStatus.getPath().getName().endsWith(".jar"))
					DistributedCache.addFileToClassPath(fileStatus.getPath(), job.getConfiguration());
			}
//			DistributedCache.addFileToClassPath(new Path(DIR_LIB+"/bounce-0.18.jar"), job.getConfiguration());
//			DistributedCache.addFileToClassPath(new Path(DIR_LIB+"/java-cup-11b-2015.03.26.jar"), job.getConfiguration());
//			DistributedCache.addFileToClassPath(new Path(DIR_LIB+"/java-cup-11b-runtime-2015.03.26.jar"), job.getConfiguration());
//			DistributedCache.addFileToClassPath(new Path(DIR_LIB+"/mtj-1.0.4.jar"), job.getConfiguration());
//			DistributedCache.addFileToClassPath(new Path(DIR_LIB+"/weka-stable-3.8.0.jar"), job.getConfiguration());
		    //---</Map 및 Reduce에 사용될 로컬캐쉬 파일 등록>----
		    
		    
		    // 입출력 데이터 경로 설정
		    FileInputFormat.addInputPath(job, new Path(args[1]));
		    FileOutputFormat.setOutputPath(job, new Path(args[2]));

		    // Job 클래스 설정
		    job.setJarByClass(GaChromSetDrive.class);
		    // Mapper 클래스 설정
		    job.setMapperClass(GaChromSetMap.class);
		    // Reducer 클래스 설정
		    job.setReducerClass(GaChromSetReduce.class);

		    // 입출력 데이터 포맷 설정
		    job.setInputFormatClass(TextInputFormat.class);
		    job.setOutputFormatClass(TextOutputFormat.class);

		    // 출력키 및 출력값 유형 설정
		    job.setOutputKeyClass(Text.class);
		    job.setOutputValueClass(Text.class);
		    
		    //---<MultipleOutputs 설정>----
		    // 개체집합 출력파일 설정
		    MultipleOutputs.addNamedOutput(job, PREFIX_CHROMSET,
		      TextOutputFormat.class, Text.class, Text.class);
		    // 개체목록 출력파일 설정(적합도 내림차순으로 정렬되지않은 개체목록)
		    MultipleOutputs.addNamedOutput(job, PREFIX_CHROM, TextOutputFormat.class,
		      Text.class, Text.class);
		    //---</MultipleOutputs 설정>----

		    // Job 수행(Job 수행완료까지 대기)
		    job.waitForCompletion(true);
		    return 0;
		}
		
	}
	
	/**
	 * 결과 정렬 MapReduce 수행을 위한 Drive 클래스
	 */
	public static class SortTextKeyDrive extends Configured implements Tool{
		
		@Override
		public int run(String[] args) throws Exception 
		{
			
		    // Job 이름 설정
			Configuration conf = getConf();
		    Job job = new Job(conf, "SortingDrive");
		    
		    
		    // 입출력 데이터 경로 설정
		    FileInputFormat.addInputPath(job, new Path(args[0]));
		    FileOutputFormat.setOutputPath(job, new Path(args[1]));

		    // Job 클래스 설정
		    job.setJarByClass(GaChromSetDrive.class);
		    // Mapper 클래스 설정
		    job.setMapperClass(SortTextKeyMap.class);
		    // Reducer 클래스 설정
		    job.setReducerClass(SortTextKeyReduce.class);
			// 정렬 순서 설정
		    job.setSortComparatorClass(TextKeyDescComparator.class);

		    // 입출력 데이터 포맷 설정
		    job.setInputFormatClass(TextInputFormat.class);
		    job.setOutputFormatClass(TextOutputFormat.class);

		    // 출력키 및 출력값 유형 설정
		    job.setOutputKeyClass(Text.class);
		    job.setOutputValueClass(Text.class);
		    
		    // Job 수행(Job 수행완료까지 대기)
		    job.waitForCompletion(true);
		    
		    return 0;
		}
		
	}
	

}
