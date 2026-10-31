package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.dto.PlanDetailVO;
import com.tongji.uav.dto.PlanHistoryVO;
import com.tongji.uav.entity.PlanningResult;
import com.tongji.uav.entity.Task;
import com.tongji.uav.entity.Waypoint;
import com.tongji.uav.mapper.PlanningResultMapper;
import com.tongji.uav.mapper.WaypointMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规划历史：历次 planning_result 列表、单次详情回看、KML/CSV 航线导出。
 * 历史记录只增不减，最新一次仍由 /latest-plan 供执行页使用。
 */
@Service
public class PlanHistoryService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlanningResultMapper planningResultMapper;
    private final WaypointMapper waypointMapper;

    public PlanHistoryService(PlanningResultMapper planningResultMapper, WaypointMapper waypointMapper) {
        this.planningResultMapper = planningResultMapper;
        this.waypointMapper = waypointMapper;
    }

    /** 某任务的历次规划记录，按时间倒序（最新在前）。 */
    public List<PlanHistoryVO> list(Long taskId) {
        List<PlanningResult> results = planningResultMapper.selectList(new LambdaQueryWrapper<PlanningResult>()
                .eq(PlanningResult::getTaskId, taskId)
                .orderByDesc(PlanningResult::getId));
        return results.stream().map(this::toHistory).collect(Collectors.toList());
    }

    /** 单次规划详情（含访问顺序与避障折线）。 */
    public PlanDetailVO detail(Long taskId, Long resultId) {
        PlanningResult pr = requireResult(taskId, resultId);
        PlanDetailVO vo = new PlanDetailVO();
        vo.setResultId(pr.getId());
        vo.setTaskId(pr.getTaskId());
        vo.setAlgorithm(pr.getAlgorithm());
        vo.setRoute(pr.getRoute());
        vo.setTotalDistanceKm(pr.getTotalDistance() == null ? null : pr.getTotalDistance().doubleValue());
        vo.setRunTimeMs(pr.getRunTimeMs());
        vo.setFeasible(pr.getFeasible());
        vo.setDetailPaths(pr.getDetailPaths());
        vo.setCreateTime(pr.getCreateTime());
        return vo;
    }

    /** 导出航线：kml（地图可直接导入）或 csv（航点顺序表）。 */
    public ExportFile export(Task task, Long resultId, String format) {
        PlanningResult pr = requireResult(task.getId(), resultId);
        List<Waypoint> ordered = orderedWaypoints(task.getId(), pr.getRoute());
        String fmt = format == null ? "kml" : format.trim().toLowerCase(Locale.ROOT);
        String alg = pr.getAlgorithm() == null ? "plan" : pr.getAlgorithm();
        String baseName = "plan-" + task.getId() + "-" + resultId + "-" + alg;
        if ("kml".equals(fmt)) {
            return new ExportFile(buildKml(task, pr, ordered).getBytes(StandardCharsets.UTF_8), baseName + ".kml",
                    "application/vnd.google-earth.kml+xml");
        }
        if ("csv".equals(fmt)) {
            return new ExportFile(buildCsv(ordered).getBytes(StandardCharsets.UTF_8), baseName + ".csv",
                    "text/csv;charset=UTF-8");
        }
        throw new IllegalArgumentException("仅支持导出 kml 或 csv");
    }

    private PlanHistoryVO toHistory(PlanningResult pr) {
        PlanHistoryVO vo = new PlanHistoryVO();
        vo.setResultId(pr.getId());
        vo.setTaskId(pr.getTaskId());
        vo.setAlgorithm(pr.getAlgorithm());
        vo.setTotalDistanceKm(pr.getTotalDistance() == null ? null : pr.getTotalDistance().doubleValue());
        vo.setRunTimeMs(pr.getRunTimeMs());
        vo.setFeasible(pr.getFeasible());
        List<Long> route = pr.getRoute();
        vo.setWaypointCount(route == null || route.isEmpty() ? 0 : Math.max(0, route.size() - 1));
        vo.setCreateTime(pr.getCreateTime());
        return vo;
    }

    private PlanningResult requireResult(Long taskId, Long resultId) {
        PlanningResult pr = planningResultMapper.selectOne(new LambdaQueryWrapper<PlanningResult>()
                .eq(PlanningResult::getId, resultId)
                .eq(PlanningResult::getTaskId, taskId));
        if (pr == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规划结果不存在");
        }
        return pr;
    }

    /**
     * 按访问顺序整理航点：route 中的 waypoint.id 依次取坐标（末位回到起点只保留一次）；
     * route 缺失时回退到航点表的 seq 顺序。
     */
    private List<Waypoint> orderedWaypoints(Long taskId, List<Long> route) {
        List<Waypoint> all = waypointMapper.selectList(new LambdaQueryWrapper<Waypoint>()
                .eq(Waypoint::getTaskId, taskId));
        Map<Long, Waypoint> byId = new LinkedHashMap<>();
        all.forEach(w -> byId.put(w.getId(), w));
        List<Waypoint> ordered = new ArrayList<>();
        if (route != null && !route.isEmpty()) {
            Set<Long> seen = new HashSet<>();
            for (Long id : route) {
                Waypoint w = byId.get(id);
                if (w != null && seen.add(id)) {
                    ordered.add(w);
                }
            }
        }
        if (ordered.isEmpty()) {
            all.sort((a, b) -> {
                int sa = a.getSeq() == null ? 0 : a.getSeq();
                int sb = b.getSeq() == null ? 0 : b.getSeq();
                return Integer.compare(sa, sb);
            });
            ordered = all;
        }
        return ordered;
    }

    private String buildKml(Task task, PlanningResult pr, List<Waypoint> ordered) {
        String lineCoords = detailLineCoords(pr, ordered);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
                .append("<Document>\n")
                .append("  <name>").append(xml(task.getTaskNo())).append(" ")
                .append(xml(task.getTaskName())).append(" 巡检航线</name>\n")
                .append("  <description>算法 ").append(xml(pr.getAlgorithm()))
                .append(" · 总航程 ").append(pr.getTotalDistance() == null ? "-" : pr.getTotalDistance().toPlainString())
                .append(" km · 规划时间 ")
                .append(pr.getCreateTime() == null ? "-" : pr.getCreateTime().format(TIME_FMT))
                .append("</description>\n")
                .append("  <Style id=\"route\"><LineStyle><color>ff0000ff</color><width>3</width></LineStyle></Style>\n")
                .append("  <Placemark>\n")
                .append("    <name>巡检航线</name>\n")
                .append("    <styleUrl>#route</styleUrl>\n")
                .append("    <LineString><tessellate>1</tessellate><coordinates>")
                .append(lineCoords)
                .append("</coordinates></LineString>\n")
                .append("  </Placemark>\n");
        int seq = 0;
        for (Waypoint w : ordered) {
            seq++;
            sb.append("  <Placemark>\n")
                    .append("    <name>").append(seq).append(". ").append(xml(displayName(w))).append("</name>\n")
                    .append("    <description>航点 #").append(w.getId())
                    .append(seq == 1 ? " · 起飞点" : " · 巡检点").append("</description>\n")
                    .append("    <Point><coordinates>").append(coord(w.getLongitude())).append(',')
                    .append(coord(w.getLatitude())).append(',')
                    .append(w.getAltitude() == null ? "0" : w.getAltitude().toPlainString())
                    .append("</coordinates></Point>\n")
                    .append("  </Placemark>\n");
        }
        sb.append("</Document>\n</kml>\n");
        return sb.toString();
    }

    private String buildCsv(List<Waypoint> ordered) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('\uFEFF');
        sb.append("序号,类型,航点ID,名称,经度,纬度,高度(m),距上一点(km),累计航程(km)\r\n");
        Waypoint prev = null;
        double cumulative = 0;
        int seq = 0;
        for (Waypoint w : ordered) {
            seq++;
            double leg = prev == null ? 0 : haversine(prev, w);
            cumulative += leg;
            sb.append(seq).append(',')
                    .append(seq == 1 ? "起飞点" : "巡检点").append(',')
                    .append(w.getId()).append(',')
                    .append(csv(displayName(w))).append(',')
                    .append(coord(w.getLongitude())).append(',')
                    .append(coord(w.getLatitude())).append(',')
                    .append(w.getAltitude() == null ? "0" : w.getAltitude().toPlainString()).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", leg)).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", cumulative)).append("\r\n");
            prev = w;
        }
        return sb.toString();
    }

    /** 折线优先用避障 detailPaths（[lat,lng]），缺失时用航点顺序连线。 */
    private String detailLineCoords(PlanningResult pr, List<Waypoint> ordered) {
        StringBuilder coords = new StringBuilder();
        List<List<Double>> detail = pr.getDetailPaths();
        if (detail != null && detail.size() >= 2) {
            for (List<Double> p : detail) {
                if (p == null || p.size() < 2) continue;
                coords.append(coord(BigDecimal.valueOf(p.get(1)))).append(',')
                        .append(coord(BigDecimal.valueOf(p.get(0)))).append(",0 ");
            }
        }
        if (coords.length() == 0) {
            for (Waypoint w : ordered) {
                coords.append(coord(w.getLongitude())).append(',')
                        .append(coord(w.getLatitude())).append(",0 ");
            }
        }
        return coords.toString().trim();
    }

    private String displayName(Waypoint w) {
        return w.getName() == null || w.getName().isBlank() ? "巡检点" + w.getId() : w.getName();
    }

    private String coord(BigDecimal v) {
        return v == null ? "0" : v.setScale(7, RoundingMode.HALF_UP).toPlainString();
    }

    private String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private double haversine(Waypoint a, Waypoint b) {
        double lat1 = Math.toRadians(a.getLatitude().doubleValue());
        double lat2 = Math.toRadians(b.getLatitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.getLongitude().doubleValue() - a.getLongitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371.0088 * Math.asin(Math.sqrt(h));
    }

    /** 导出文件：内容 + 文件名 + MIME。 */
    public record ExportFile(byte[] content, String filename, String contentType) {
    }
}
