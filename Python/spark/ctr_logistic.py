from __future__ import print_function
from pyspark import SparkContext
from pyspark.mllib.classification import LogisticRegressionWithLBFGS, LogisticRegressionModel
from pyspark.mllib.regression import LabeledPoint
from pyspark.mllib.util import MLUtils
import json

#(' 101356, 101356, 32714, 32714, 5963, 10594, 21240, 34825, 5963, 7959,1000000', 1)
if __name__ == "__main__":

    sc = SparkContext(appName="CTRLogisticRegression")

    # $example on$
    # Load and parse the data
    def parsePoint(line):
        line = line.strip("()")
        fields = line.split(',')
        featurs_raw = fields[0:11] # 0 to 10
        features = []
        for x in featurs_raw:
            feature = float(x.strip().strip("'").strip())
            features.append(feature)

        label = float(fields[11])
        #print ("label=" + str(label))
        return LabeledPoint(label,features)

    DIR = "../../data/"
    data = sc.textFile(DIR + "log/ctr_features/part*")

    # DIR2 = "../../data2/"
    # data = sc.textFile(DIR2 + "log/ctr_features/part*")

    (trainingData, testData) = data.randomSplit([0.7, 0.3])

    parsedTrainData = trainingData.map(parsePoint)
    parsedTestData = testData.map(parsePoint)

    # Build the model
    model = LogisticRegressionWithLBFGS.train(parsedTrainData,intercept=False)

    # Evaluating the model on training data
    labelsAndPreds = parsedTestData.map(lambda p: (p.label, model.predict(p.features)))
    trainErr = labelsAndPreds.filter(lambda vp: vp[0] != vp[1]).count() / float(parsedTestData.count())

    print("Training Error = " + str(trainErr))
    weights = model.weights
    #print(model.toDebugString())
    print("weight= ", weights)
    print("bias=",model.intercept);

    bias = model.intercept

    # Save weights and bias to a text file
    model_info = {"weights": weights.tolist(), "bias": bias}
    # with open(DIR + "model/ctrLogisticRegression.txt", 'w') as file:
    with open(DIR + "model/ctrLogisticRegression.txt", 'w') as file:
        json.dump(model_info, file)

    # Save and load model
    # model.save(sc, DIR + "model/ctr_logistic_model")
    model.save(sc, DIR + "model/ctr_logistic_model")
    # $example off$