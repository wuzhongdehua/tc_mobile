package org.com.tianchi.base

import org.apache.spark.mllib.classification.{LogisticRegressionModel, SVMModel}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.model.{GradientBoostedTreesModel, RandomForestModel}
import org.apache.spark.rdd.RDD

//一定要序列化
object BaseComputing extends Serializable {
  //转化为LabelPoint dly 123 4 5
  def toLablePoint(data: RDD[(String, Array[Double])], label: Set[String]): RDD[(String, LabeledPoint)] = {
    data.map(line => {
      var s = new LabeledPoint(0.0, Vectors.dense(line._2))
      if (label.contains(line._1)) s = new LabeledPoint(1.0, Vectors.dense(line._2))
      (line._1, s)
    })
  }

  def getSelectFeatureData(data: RDD[(String, LabeledPoint)], item: Set[String]) = {
    data.filter(line => item.contains(line._1.split("_")(1)))
  }

  //获取商品子集item_id,已去重
  def getItemSet(data: RDD[String]): Set[String] = {
    data.map(_.split(",")(0)).collect().toSet
  }

  //逻辑回归预测,num为预测的规模
  def lrPredict(data: RDD[(String, LabeledPoint)], model: LogisticRegressionModel, num: Int): Array[(String, Double)] = {
    data.map { case (userItem, LabeledPoint(label, features)) =>
      val prediction = model.clearThreshold().predict(Vectors.dense(features.toArray.map(line => Math.log(line + 1)))) //做了log处理
      (prediction, (userItem, label))
    }.top(num).map(_._2)
  }

  //noinspection SizeToLength,FilterSize
  //计算F值
  def calFvalue(data: Array[(String, Double)], buyedNextDay: Set[String]): String = {
    val count = data.size
    val orgin = buyedNextDay.size
    val acc = data.filter(_._2 == 1.0).size
    val precision = acc.toDouble / count
    val recall = acc.toDouble / orgin
    "predict_num:" + count + " precision:" + precision + " recall:" + recall + " F1:" + 2 * (recall * precision) / (precision + recall)
  }

  def svmPredict(data: RDD[(String, LabeledPoint)], model: SVMModel, num: Int): Array[(String, Double)] = {
    data.map { case (userItem, LabeledPoint(label, features)) =>
      val prediction = model.clearThreshold().predict(Vectors.dense(features.toArray.map(line => Math.log(line + 1)))) //做了log处理
      (prediction, (userItem, label))
    }.top(num).map(_._2)
  }

  def gbrtPredict(data: RDD[(String, LabeledPoint)], model: GradientBoostedTreesModel, num: Int): Array[(String, Double)] = {
    data.map { case (userItem, LabeledPoint(label, features)) =>
      val prediction = model.predict(features)
      (prediction, (userItem, label))
    }.top(num).map(_._2)
  }

  def rfPredict(data: RDD[(String, LabeledPoint)], model: RandomForestModel, num: Int): Array[(String, Double)] = {
    data.map { case (userItem, LabeledPoint(label, features)) =>
      val prediction = model.predict(features)
      (prediction, (userItem, label))
    }.top(num).map(_._2)
  }

  //noinspection ComparingUnrelatedTypes
  def getBuyLabel(data: RDD[String], date: String): Set[String] = {
    data.filter(_.split(",")(5).split(" ")(0).equals(date)).
      filter(_.split(",")(2).equals("4")).map(line => {
      line.split(",")(0) + "_" + line.split(",")(1) + "_" + line.split(",")(4)
    }).distinct().collect().toSet
  }

  //返回(userid_itemid_itemCategory,Array[Record]),按照时间顺序排序
  def getUserItemData(data: RDD[String]) = {
    data.map(line => (line.split(",")(0) + "_" + line.split(",")(1) + "_" + line.split(",")(4), line)).
      groupByKey().map(line => (line._1, line._2.toArray.map(new UserRecord(_)) sortBy (_.time)))
  }

  def getItemGeoHash(data: RDD[String]) = {
    data.map(line => (line.split(",")(0), line)).
      groupByKey().map(line => (line._1, line._2.toArray.map(new ItemRecord(_))))
  }

  def getUserData(data: RDD[String]) = {
    data.map(line => (line.split(",")(0), line)).
      groupByKey().map(line => (line._1, line._2.toArray.map(new UserRecord(_)) sortBy (_.time)))

  }

  def getItemData(data: RDD[String]) = {
    data.map(line => (line.split(",")(1), line)).
      groupByKey().map(line => (line._1, line._2.toArray.map(new UserRecord(_)) sortBy (_.time)))
  }

  def getCategoryData(data: RDD[String]) = {
    data.map(line => (line.split(",")(4), line)).
      groupByKey().map(line => (line._1, line._2.toArray.map(new UserRecord(_)) sortBy (_.time)))
  }

  //特征join
  def join(userItem: RDD[(String, Array[Double])],
           item: RDD[(String, Array[Double])],
           user: RDD[(String, Array[Double])],
           geo: RDD[(String, Array[Double])]): RDD[(String, Array[Double])] = {
    //和物品进行join
    val useritemJoinItem = userItem.map { case (user_item_cat_id, user_item_features) =>
      val item_id = user_item_cat_id.split("_")(1)
      (item_id, (user_item_cat_id, user_item_features))
    }.join(item).map { case (item_id, ((user_item_cat_id, user_item_features), itemFeatures)) =>
      (user_item_cat_id, user_item_features ++ itemFeatures)
    }
    val joinGeo = useritemJoinItem.map {
      case (user_item_cat_id, features) =>
        val user_id = user_item_cat_id.split("_")(0)
        val item_id = user_item_cat_id.split("_")(0)
        (user_id + "_" + item_id, (user_item_cat_id, features))
    }.join(geo).map {
      case (user_item_id, ((user_item_cat_id, features), geo_features)) =>
        (user_item_cat_id, features ++ geo_features)
    }
    val userMap = user.collect().toMap
    useritemJoinItem.map(line => {
      val userid = line._1.split("_")(0)
      val result = line._2.toBuffer ++ userMap(userid)
      (line._1, result.toArray)
    })
  }
}
