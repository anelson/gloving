package gloving

import org.apache.spark.SparkConf

import breeze.linalg.DenseVector

object SparkHelpers {
	def config(conf: SparkConf): SparkConf = {
		//Use the Kryo serializer for better memory efficiency
		val newConf = conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
			.set("spark.kryo.registrationRequired", "true")

		newConf.registerKryoClasses(Array(
			classOf[WordVector],
			classOf[(WordVector, Double)],
			classOf[Array[WordVector]],
			classOf[Array[(WordVector, Double)]]
			)
		)

		//Now register Breeze classes
		newConf.registerKryoClasses(Array(
			classOf[DenseVector[Double]],
			Class.forName("breeze.linalg.DenseVector$mcD$sp")
			)
		)

		//Now register the Spark built-in classes that should have been registered by Spark itself but
		//isn't.  I doubt very much the commitment of the Spark team to maximizing Kryo performance
		//given how hard this is
		newConf.registerKryoClasses(Array(
			classOf[Object],
			classOf[Array[Object]],
			classOf[Array[Double]],
			Class.forName("scala.Tuple2"),
			java.lang.reflect.Array.newInstance(Class.forName("scala.Tuple2"), 0).getClass,
			java.lang.reflect.Array.newInstance(java.lang.reflect.Array.newInstance(Class.forName("scala.Tuple2"), 0).getClass, 0).getClass,

			Class.forName("scala.collection.mutable.WrappedArray$ofRef"),
			Class.forName("scala.collection.immutable.Map$EmptyMap$"),
			Class.forName("scala.collection.mutable.ArraySeq"),
			Class.forName("scala.None$"),

			Class.forName("org.apache.spark.sql.types.StructType"),
			java.lang.reflect.Array.newInstance(Class.forName("org.apache.spark.sql.types.StructType"), 0).getClass,

			Class.forName("org.apache.spark.sql.types.StructField"),
			java.lang.reflect.Array.newInstance(Class.forName("org.apache.spark.sql.types.StructField"), 0).getClass,

			Class.forName("org.apache.spark.sql.types.StringType$"),
			Class.forName("org.apache.spark.sql.types.LongType$"),
			Class.forName("org.apache.spark.sql.types.Metadata"),
			Class.forName("org.apache.spark.mllib.linalg.VectorUDT"),
			Class.forName("org.apache.spark.sql.catalyst.expressions.InterpretedOrdering"),
			Class.forName("org.apache.spark.sql.catalyst.expressions.SortOrder"),
			Class.forName("org.apache.spark.sql.catalyst.expressions.BoundReference"),
			Class.forName("org.apache.spark.sql.catalyst.expressions.Ascending$"),
			Class.forName("org.apache.spark.sql.catalyst.trees.Origin"),

			Class.forName("org.apache.spark.mllib.linalg.DenseVector")
			)
		)

		newConf
	}
}