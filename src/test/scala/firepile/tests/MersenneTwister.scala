package firepile.tests

import firepile._
import firepile.Device
import firepile.Arrays._
import firepile.Spaces._
import firepile.util.BufferBackedArray._
import firepile.tree.Trees.Tree
import com.nativelibs4java.opencl._
import com.nativelibs4java.util._
import java.nio.FloatBuffer
import java.nio.ByteOrder
import scala.collection.JavaConversions._
import firepile.Marshaling._
import java.io._
import scala.util.Random
import scala.math.{ceil, pow, log, abs}
import firepile.util.Unsigned._
import firepile.tests.MersenneTwisterDataReader._
import firepile.tests.CPUMersenneTwister._

object MersenneTwister {

val MT_RNG_COUNT=4096

//val MT_RNG_COUNT: Int=1024
val MT_MM:Int =9
val MT_NN: Int=19
val MT_WMASK: Short =0xFFFFFFFF
val MT_UMASK: Short =0xFFFFFFFE
val MT_LMASK: Short =0x1
val MT_SHIFT0: Int=12
val MT_SHIFTB: Int =7
val MT_SHIFTC: Int=15
val MT_SHIFT1: Int=18
val PI=3.14159265358979f

//val globalWorkSize= MT_RNG_COUNT      // 1D var for Total # of work items
val localWorkSize =  128                // 1D var for # of work items in the work group	
//val localWorkSize: Int =  64
val seed: UInt = 777.toUInt
val nPerRng: Int = 5860                      // # of recurrence steps, must be even if do Box-Muller transformation
val nRand = MT_RNG_COUNT * nPerRng  

//val NUM_ITEMS = 16384 // 1048576
//val globalWorkSize: Int = 16384
val globalWorkSize: Int = MT_RNG_COUNT	
val maxThreads: Int = 512 
val maxBlocks: Int = 128

  def main(args: Array[String]) = run
  
  def run = {
  
      val (a,b,c,d) = readData(".\\data\\MersenneTwister.dat")
      
      val h_RandGPU= RandomNumGen(a,b,c,d,nPerRng)
      
      /*
      val h_RandCPU = cpuMersenneTwister(a,b,c)
      
      //println(" Sample random numbers ")
      var sum_delta= 0d
      var sum_ref = 0d
      for(i <- 0 until MT_RNG_COUNT)
          for( j <- 0 until nPerRng) {
      	        var rCPU = h_RandCPU(i * nPerRng + j)
      	        var rGPU = h_RandGPU(i + j * MT_RNG_COUNT)
      	        var delta = abs(rCPU - rGPU)
      	        sum_delta += delta
      	        sum_ref   += abs(rCPU)
      	        }
      val norm = sum_delta / sum_ref
      val datasize = nPerRng * MT_RNG_COUNT * 
      var data = new Array[Byte](numberBytes)
                  String file = ".\\data\\MersenneTwisterResults.dat"
                  FileOutputStream fileoutputstream = new FileOutputStream(file)
      
                  for (i <- data.length)
                    fileoutputstream.write(data(i))
                  
      
      fileoutputstream.close();
      
      println("Difference:::"+ sum_delta )
      if( norm < 1E-6) println("PASSED") else println("FAILED")
      
      */
      
      for( i <-0 until h_RandGPU.length) 
      println(" Number::"+ h_RandGPU(i*MT_RNG_COUNT))
    
  }
  
 
  def RandomNumGen(matrix_a : Array[UInt], mask_a: Array[UInt], mask_b: Array[UInt], seed : Array[UInt], n: Int): Array[Float] = {
    implicit val gpu: Device = firepile.gpu
    gpu.setWorkSizes(globalWorkSize, localWorkSize)
    val threads = (if (matrix_a.length < gpu.maxThreads*2) pow(2, ceil(log(matrix_a.length) / log(2))) else gpu.maxThreads).toInt
    val blocks = ((matrix_a.length + (threads * 2 - 1)) / (threads * 2)).toInt
   
    // val add: (Float, Float) => Float = _+_

    val RandomNumberGenerator : (Array[UInt], Array[UInt], Array[UInt], Array[UInt], Array[Int], Array[Float]) => Unit = firepile.Compiler.compile {
      (A: Array[UInt], B: Array[UInt], C: Array[UInt], D: Array[UInt], n: Array[Int], E: Array[Float]) => MersenneTwister(A, B, C, D, n,E)
    }
    
     
    val d_Rand : Array[Float] = new Array[Float](nRand)
    val nn = new Array[Int](globalWorkSize) 
    RandomNumberGenerator(matrix_a, mask_a, mask_b, seed, nn, d_Rand)
    d_Rand
 }
  
////////////////////////////////////////////////////////////////////////////////
// OpenCL Kernel for Mersenne Twister RNG
////////////////////////////////////////////////////////////////////////////////
def MersenneTwister(matrix_a: Array[UInt], mask_b: Array[UInt], mask_c: Array[UInt],seed: Array[UInt], n: Array[Int] ,d_Rand: Array[Float]) = 
 (id: Id1, mt: Array[UInt]) => {
    val MT_RNG_COUNT: Int=4096
    val MT_MM: Int=9
    val MT_NN: Int=19
    val MT_WMASK: Short =0xFFFFFFFF
    val MT_UMASK: Short =0xFFFFFFFE
    val MT_LMASK: Short =0x1
    val MT_SHIFT0: Int=12
    val MT_SHIFTB: Int=7
    val MT_SHIFTC: Int=15
    val MT_SHIFT1: Int =18
    val PI=3.14159265358979f
    val nPerRng:Int = 5860 
    val localSeed: UInt = 777.toUInt
    
    val i = id.group
    var iState: Int = 0
    var iState1: Int = 0
    var iStateM: Int = 0
    var iOut: Int = 0
    var mti: UInt = iOut.toUInt
    var mti1: UInt = mti
    var mtiM: UInt = mti
    var x : UInt = mti
    //var mt =new Array[UInt](MT_NN)
    var m_a: UInt = mti
    var m_b: UInt = mti
    var m_c: UInt = mti
    var cond: UInt =mti
   
    var something: UInt = ((mti & ( mti1 / 2.toUInt ).toUInt) - 1.toUInt).toUInt
    //Load bit-vector Mersenne Twister parameters
    m_a   = matrix_a(i)
    m_b   = mask_b(i)
    m_c   = mask_c(i)
        
    //Initialize current state
    
    mt(0) = localSeed
        iState = 1
        while(iState < MT_NN) {
        mt(iState) = ((1812433253.toShort * (mt(iState - 1) ^ (mt(iState - 1) >> 30)) + iState) & MT_WMASK).toUInt;
        iState+=1
        }

    iState = 0
    mti1 = mt(0)

    iOut = 0
    while(iOut < nPerRng) {
    
        iState1 = iState + 1
        iStateM = iState + MT_MM
        if(iState1 >= MT_NN) iState1 -= MT_NN
        if(iStateM >= MT_NN) iStateM -= MT_NN
        mti  = mti1
        mti1 = mt(iState1)
        mtiM = mt(iStateM)

	    // MT recurrence
        x = (mti & MT_UMASK.toUInt) | (mti1 & MT_LMASK.toUInt)
        cond = x & 1.toUInt
            if(cond>0.toUInt)        
	    x = mtiM ^ (x >> 1.toUInt) ^ ( m_a)
	    else
	    x = mtiM ^ (x >> 1.toUInt) ^ (0.toUInt)

        mt(iState) = x
        iState = iState1

        //Tempering transformation
        x ^= (x >> MT_SHIFT0)
        x ^= (x << MT_SHIFTB) & m_b
        x ^= (x << MT_SHIFTC) & m_c
        x ^= (x >> MT_SHIFT1)

        //Convert to (0, 1] float and write to global memory
        d_Rand(i + iOut * MT_RNG_COUNT) = (x.toFloat + 1.0f) / 4294967296.0f;
        iOut+=1
    }
    
    
    //d_Rand(i + iOut * MT_RNG_COUNT) = (x.toFloat + 1.0f) / 4294967296.0f;
  }
  
  
  /*//////////////////////////////////////////////////////////////////////////////
  // Transform each of MT_RNG_COUNT lanes of nPerRng uniformly distributed
  // random samples, produced by MersenneTwister(), to normally distributed lanes
  // using Cartesian form of Box-Muller transformation.
  // nPerRng must be even.
  //////////////////////////////////////////////////////////////////////////////
  
  void BoxMullerTrans(__global float *u1, __global float *u2)
  {
      float   r = sqrt(-2.0f * log(*u1));
      float phi = 2 * PI * (*u2);
      *u1 = r * native_cos(phi);
      *u2 = r * native_sin(phi);
  }
  
  def BoxMuller( d_Rand: Float, nPerRng: Int) 
  {
      int i = id.group
  
      for (int iOut = 0; ; iOut += 2)
          while(iOut < nPerRng) {
          BoxMullerTrans(&d_Rand[globalID + (iOut + 0) * MT_RNG_COUNT],&d_Rand[globalID + (iOut + 1) * MT_RNG_COUNT])
          }
  }
*/

}
