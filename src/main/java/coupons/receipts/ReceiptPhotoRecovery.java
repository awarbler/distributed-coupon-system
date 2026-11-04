package coupons.receipts;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Local receipt photo preparation at native resolution. Never synthesizes text or sends an image off-device. */
final class ReceiptPhotoRecovery {
    private ReceiptPhotoRecovery() {}

    static int score(String text) {
        int labels=(int)java.util.regex.Pattern.compile("(?i)\\b(?:walgreens|subtotal|total|regular price|return value|savings|cash|sale|RFN)\\b").matcher(text).results().count();
        int amounts=(int)java.util.regex.Pattern.compile("\\b\\d+[.,]\\d{2}\\b").matcher(text).results().count();
        return labels*3+Math.min(amounts,40);
    }

    static Path prepare(Path source,Path work) throws IOException {
        BufferedImage original=ImageIO.read(source.toFile());
        if(original==null)return source;
        try {
            BufferedImage prepared=prepare(original);
            try {Path target=work.resolve("receipt-prepared.png");ImageIO.write(prepared,"png",target.toFile());return target;}
            finally {prepared.flush();}
        } finally {original.flush();}
    }

    static BufferedImage prepare(BufferedImage original) {
        // Bound segmentation memory for large photographs; keep native detail on larger scans.
        int originalW=original.getWidth(),originalH=original.getHeight();
        double analysisScale=Math.min(1,Math.sqrt(1_000_000.0/((double)originalW*originalH)));
        int w=Math.max(1,(int)(originalW*analysisScale)),h=Math.max(1,(int)(originalH*analysisScale));
        // Detect paper on a bounded thumbnail; sample final pixels from the original.
        var analysis=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        var graphics=analysis.createGraphics();graphics.drawImage(original,0,0,w,h,null);graphics.dispose();
        byte[] paper=new byte[w*h];int[] queue=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int rgb=analysis.getRGB(x,y),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
            int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
            if(min>140&&max-min<85)paper[y*w+x]=1;
        }
        int[] left=new int[h],right=new int[h];Arrays.fill(left,w);Arrays.fill(right,-1);
        int best=0;
        for(int start=0;start<paper.length;start++) {
            if(paper[start]==0)continue;
            int count=1;queue[0]=start;paper[start]=0;
            for(int i=0;i<count;i++) {
                int p=queue[i],x=p%w,y=p/w;
                if(x>0&&paper[p-1]!=0){paper[p-1]=0;queue[count++]=p-1;}
                if(x+1<w&&paper[p+1]!=0){paper[p+1]=0;queue[count++]=p+1;}
                if(y>0&&paper[p-w]!=0){paper[p-w]=0;queue[count++]=p-w;}
                if(y+1<h&&paper[p+w]!=0){paper[p+w]=0;queue[count++]=p+w;}
            }
            if(count>best) {
                best=count;Arrays.fill(left,w);Arrays.fill(right,-1);
                for(int i=0;i<count;i++){int x=queue[i]%w,y=queue[i]/w;left[y]=Math.min(left[y],x);right[y]=Math.max(right[y],x);}
            }
        }
        int top=0,bottom=h-1;while(top<h&&right[top]<0)top++;while(bottom>top&&right[bottom]<0)bottom--;
        boolean crop=best>w*h/12&&bottom-top>h/2;
        double l0=0,l1=0,r0=w-1,r1=w-1;
        if(crop) {
            // Fit both paper edges over the middle of the receipt to ignore torn corners.
            double sy=0,syy=0,sl=0,sr=0,syl=0,syr=0,n=0;
            for(int y=top+(bottom-top)/10;y<bottom-(bottom-top)/10;y++)if(right[y]-left[y]>w/6){n++;sy+=y;syy+=(double)y*y;sl+=left[y];sr+=right[y];syl+=(double)y*left[y];syr+=(double)y*right[y];}
            if(n>1&&n*syy-sy*sy>0) {
                double ls=(n*syl-sy*sl)/(n*syy-sy*sy),rs=(n*syr-sy*sr)/(n*syy-sy*sy);
                double li=(sl-ls*sy)/n,ri=(sr-rs*sy)/n;
                l0=Math.max(0,li+ls*top-4);l1=Math.max(0,li+ls*bottom-4);
                r0=Math.min(w-1,ri+rs*top+4);r1=Math.min(w-1,ri+rs*bottom+4);
                if(r0-l0<w/6||r1-l1<w/6)crop=false;
            } else crop=false;
        }
        if(!crop){top=0;bottom=h-1;l0=l1=0;r0=r1=w-1;}
        analysis.flush();
        double sx=(double)originalW/w,sy=(double)originalH/h;
        l0*=sx;l1*=sx;r0=Math.min(originalW-1,(r0+1)*sx-1);r1=Math.min(originalW-1,(r1+1)*sx-1);
        top=(int)(top*sy);bottom=Math.min(originalH-1,(int)((bottom+1)*sy)-1);
        w=originalW;h=originalH;
        int width=(int)Math.ceil(Math.max(r0-l0,r1-l1))+1,height=bottom-top+1;
        double scale=Math.min(4,Math.max(1,1200.0/width));
        scale=Math.max(1,Math.min(scale,Math.sqrt(25_000_000.0/((double)width*height))));
        int outW=Math.max(1,(int)(width*scale)),outH=Math.max(1,(int)(height*scale));
        BufferedImage result=new BufferedImage(outW,outH,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<outH;y++) {
            double fy=outH==1?0:(double)y/(outH-1),srcY=top+fy*(bottom-top),leftX=l0+fy*(l1-l0),rightX=r0+fy*(r1-r0);
            for(int x=0;x<outW;x++) {
                double srcX=leftX+(outW==1?0:(double)x/(outW-1))*(rightX-leftX);
                int x0=Math.max(0,Math.min(w-1,(int)srcX)),y0=Math.min(h-1,(int)srcY),x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
                double dx=srcX-x0,dy=srcY-y0;int rgb=0;
                for(int shift=16;shift>=0;shift-=8) {
                    double a=((original.getRGB(x0,y0)>>shift)&255)*(1-dx)+((original.getRGB(x1,y0)>>shift)&255)*dx;
                    double b=((original.getRGB(x0,y1)>>shift)&255)*(1-dx)+((original.getRGB(x1,y1)>>shift)&255)*dx;
                    rgb|=((int)Math.round(a*(1-dy)+b*dy))<<shift;
                }
                result.setRGB(x,y,rgb);
            }
        }
        return result;
    }

    static LocalReceiptOcr.Recognition nativeRead(Path source,Path work) throws IOException {
        Path script=work.resolve("receipt-vision.swift"),output=work.resolve("vision.tsv"),log=work.resolve("vision.log");
        Files.writeString(script,SWIFT,StandardCharsets.UTF_8);
        Process process=new ProcessBuilder("/usr/bin/swift",script.toString(),source.toString(),output.toString())
                .redirectOutput(log.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(!process.waitFor(60,TimeUnit.SECONDS)||process.exitValue()!=0||!Files.exists(output))throw new IOException("Local photo recovery unavailable.");
            return fromObservations(Files.readAllLines(output));
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Photo recovery interrupted.",e);}
        finally {process.destroyForcibly();}
    }

    record Observation(double x,double y,double width,double height,String text) {}
    static LocalReceiptOcr.Recognition fromObservations(List<String> observations) {
        List<Observation> words=new ArrayList<>();
        for(String line:observations){String[] f=line.split("\\t",5);if(f.length==5)words.add(new Observation(Double.parseDouble(f[0]),Double.parseDouble(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),f[4]));}
        words.sort(Comparator.comparingDouble(Observation::y));
        List<List<Observation>> rows=new ArrayList<>();
        for(var word:words) {
            List<Observation> row=rows.isEmpty()?null:rows.getLast();
            if(row==null||Math.abs(word.y()+word.height()/2-row.getFirst().y()-row.getFirst().height()/2)>Math.min(word.height(),row.getFirst().height())*.55){row=new ArrayList<>();rows.add(row);}
            row.add(word);
        }
        StringBuilder text=new StringBuilder(),tsv=new StringBuilder("level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n");
        int line=0;
        for(var row:rows){row.sort(Comparator.comparingDouble(Observation::x));line++;int i=0;for(var word:row){if(i>0)text.append(' ');text.append(word.text());tsv.append("5\t1\t1\t1\t").append(line).append('\t').append(++i).append('\t').append(Math.round(word.x()*10000)).append('\t').append(Math.round(word.y()*10000)).append('\t').append(Math.round(word.width()*10000)).append('\t').append(Math.round(word.height()*10000)).append("\t-1\t").append(word.text()).append('\n');}text.append('\n');}
        return new LocalReceiptOcr.Recognition(text.toString(),tsv.toString());
    }

    private static final String SWIFT="""
        import Foundation
        import Vision
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        request.minimumTextHeight = 0.003
        do {
            try VNImageRequestHandler(url: URL(fileURLWithPath: CommandLine.arguments[1])).perform([request])
            // Correct reading order for the slight camera tilt that otherwise pairs
            // a right-column amount with the following left-column label.
            let slopes = (request.results ?? []).compactMap { observation -> Double? in
                guard let text = observation.topCandidates(1).first,
                      text.string.count >= 8, observation.boundingBox.width > 0.25,
                      let box = try? text.boundingBox(for: text.string.startIndex..<text.string.endIndex) else { return nil }
                let dx = box.bottomRight.x - box.bottomLeft.x
                guard dx > 0.1 else { return nil }
                let slope = -(box.bottomRight.y - box.bottomLeft.y) / dx
                return abs(slope) < 0.2 ? Double(slope) : nil
            }.sorted()
            let slope = slopes.isEmpty ? 0 : slopes[slopes.count / 2]
            let lines = (request.results ?? []).compactMap { observation -> String? in
                guard let text = observation.topCandidates(1).first else { return nil }
                let b = observation.boundingBox
                return "\\(b.minX)\\t\\(1-b.maxY - slope * (b.midX - 0.5))\\t\\(b.width)\\t\\(b.height)\\t\\(text.string)"
            }
            try lines.joined(separator: "\\n").write(toFile: CommandLine.arguments[2], atomically: true, encoding: .utf8)
        } catch { exit(1) }
        """;
}
