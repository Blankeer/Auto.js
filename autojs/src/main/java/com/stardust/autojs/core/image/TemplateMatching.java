package com.stardust.autojs.core.image;

import android.util.Pair;
import android.util.TimingLogger;

import com.stardust.util.Nath;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Stardust on 2017/11/25.
 */

public class TemplateMatching {

    private static final String LOG_TAG = "TemplateMatching";

    public static final int MAX_LEVEL_AUTO = -1;
    public static final int MATCHING_METHOD_DEFAULT = Imgproc.TM_CCOEFF_NORMED;

    public static Point fastTemplateMatching(Mat img, Mat template, float threshold) {
        return fastTemplateMatching(img, template, MATCHING_METHOD_DEFAULT, 0.75f, threshold, MAX_LEVEL_AUTO);
    }


    public static Point fastTemplateMatching(Mat img, Mat template, int matchMethod, float weakThreshold, float strictThreshold, int maxLevel) {
        TimingLogger logger = new TimingLogger(LOG_TAG, "fast_tm");
        if (maxLevel == MAX_LEVEL_AUTO) {
            maxLevel = selectPyramidLevel(img, template);
            logger.addSplit("selectPyramidLevel:" + maxLevel);
        }
        Point p = null;
        Mat matchResult;
        double similarity = 0;
        for (int level = maxLevel; level >= 0; level--) {
            Mat src = getPyramidDownAtLevel(img, level);
            Mat currentTemplate = getPyramidDownAtLevel(template, level);
            if (p == null) {
                if (!shouldContinueMatching(level, maxLevel)) {
                    break;
                }
                matchResult = matchTemplate(src, currentTemplate, matchMethod);
                Pair<Point, Double> bestMatched = getBestMatched(matchResult, matchMethod, weakThreshold);
                p = bestMatched.first;
                similarity = bestMatched.second;
            } else {
                Rect r = getROI(p, src, currentTemplate);
                matchResult = matchTemplate(new Mat(src, r), currentTemplate, matchMethod);
                Pair<Point, Double> bestMatched = getBestMatched(matchResult, matchMethod, weakThreshold);
                if (bestMatched.second < weakThreshold) {
                    p = null;
                    break;
                }
                p = bestMatched.first;
                similarity = bestMatched.second;
                p.x += r.x;
                p.y += r.y;
                if (bestMatched.second >= strictThreshold) {
                    pyrUp(p, level);
                    break;
                }
            }
            logger.addSplit("level:" + level + " point:" + p);
        }
        logger.addSplit("result:" + p);
        logger.dumpToLog();
        if (similarity < strictThreshold) {
            return null;
        }
        return p;
    }

    private static Mat getPyramidDownAtLevel(Mat m, int level) {
        if (level == 0) {
            return m;
        }
        int cols = m.cols();
        int rows = m.rows();
        for (int i = 0; i < level; i++) {
            cols = (cols + 1) / 2;
            rows = (rows + 1) / 2;
        }
        Mat r = new Mat(rows, cols, m.type());
        Imgproc.resize(m, r, new Size(cols, rows));
        return r;
    }

    private static void pyrUp(Point p, int level) {
        for (int i = 0; i < level; i++) {
            p.x *= 2;
            p.y *= 2;
        }
    }

    private static boolean shouldContinueMatching(int level, int maxLevel) {
        if (level == maxLevel && level != 0) {
            return true;
        }
        if (maxLevel <= 2) {
            return false;
        }
        return level == maxLevel - 1;
    }

    private static Rect getROI(Point p, Mat src, Mat currentTemplate) {
        int x = (int) (p.x * 2 - currentTemplate.cols() / 4);
        x = Math.max(0, x);
        int y = (int) (p.y * 2 - currentTemplate.rows() / 4);
        y = Math.max(0, y);
        int w = (int) (currentTemplate.cols() * 1.5);
        int h = (int) (currentTemplate.rows() * 1.5);
        if (x + w >= src.cols()) {
            w = src.cols() - x - 1;
        }
        if (y + h >= src.rows()) {
            h = src.rows() - y - 1;
        }
        return new Rect(x, y, w, h);
    }

    private static int selectPyramidLevel(Mat img, Mat template) {
        int minDim = Nath.min(img.rows(), img.cols(), template.rows(), template.cols());
        //这里选取12为图像缩小后的最小宽高，从而用log(2, minDim / 16)得到最多可以经过几次缩小。
        int maxLevel = (int) (Math.log(minDim / 7) / Math.log(2));
        if (maxLevel < 0) {
            return 0;
        }
        //上限为6
        return Math.min(6, maxLevel);
    }


    public static List<Mat> buildPyramid(Mat mat, int maxLevel) {
        List<Mat> pyramid = new ArrayList<>();
        pyramid.add(mat);
        for (int i = 0; i < maxLevel; i++) {
            Mat m = new Mat((mat.rows() + 1) / 2, (mat.cols() + 1) / 2, mat.type());
            Imgproc.pyrDown(mat, m);
            pyramid.add(m);
            mat = m;
        }
        return pyramid;
    }

    public static Mat matchTemplate(Mat img, Mat temp, int match_method) {
        int result_cols = img.cols() - temp.cols() + 1;
        int result_rows = img.rows() - temp.rows() + 1;
        Mat result = new Mat(result_rows, result_cols, CvType.CV_32FC1);
        Imgproc.matchTemplate(img, temp, result, match_method);
        return result;
    }

    public static Pair<Point, Double> getBestMatched(Mat tmResult, int matchMethod, float threshold) {
        TimingLogger logger = new TimingLogger(LOG_TAG, "best_matched_point");
        // FIXME: 2017/11/26 正交化?
        //   Core.normalize(tmResult, tmResult, 0, 1, Core.NORM_MINMAX, -1, new Mat());
        Core.MinMaxLocResult mmr = Core.minMaxLoc(tmResult);
        logger.addSplit("minMaxLoc");
        double value;
        Point pos;
        if (matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) {
            pos = mmr.minLoc;
            value = -mmr.minVal;
        } else {
            pos = mmr.maxLoc;
            value = mmr.maxVal;
        }
        logger.addSplit("value:" + value);
        logger.dumpToLog();
        return new Pair<>(pos, value);
    }


}
