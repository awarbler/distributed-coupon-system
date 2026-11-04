package coupons.api;

import java.util.*;

/** Joins overlapping OCR sequences, preserving repeated product and coupon occurrences. */
final class ReceiptPhotoMerge {
    private ReceiptPhotoMerge() {}
    static List<Map<String,Object>> combine(List<Map<String,Object>> inputs) {
        var result=new ArrayList<>(inputs);
        boolean changed;
        do {
            changed=false;
            outer:for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++) {
                var a=result.get(i);var b=result.get(j);
                if(!Boolean.TRUE.equals(a.get("scanned"))||!Boolean.TRUE.equals(b.get("scanned")))continue;
                String first=a.get("number").toString(),second=b.get("number").toString();
                if(!first.isBlank()&&!second.isBlank()&&!first.equals(second))continue;
                String joined=join(a.get("rawText").toString(),b.get("rawText").toString());
                if(joined==null)continue;
                result.set(i,ReceiptImport.parse(a.get("name")+" + "+b.get("name"),joined,true));result.remove(j);
                changed=true;break outer;
            }
        }while(changed);
        return result;
    }
    static String join(String first,String second) {
        var a=first.lines().filter(s->!s.isBlank()).toList();var b=second.lines().filter(s->!s.isBlank()).toList();
        int best=0,bestOffset=0;
        for(int offset=-b.size()+3;offset<=a.size()-3;offset++) {
            int start=Math.max(0,offset),end=Math.min(a.size(),offset+b.size()),count=end-start;
            if(count<3||count<=best)continue;
            boolean equal=true,anchor=false;
            for(int k=start;k<end;k++) {
                String x=normalize(a.get(k)),y=normalize(b.get(k-offset));
                if(!x.equals(y)){equal=false;break;}
                // Store/address boilerplate alone is insufficient to identify a receipt.
                if(x.matches(".*(?:\\b\\d{10,14}\\b|RFN.*\\d|\\d{1,2}/\\d{1,2}/\\d{4}).*"))anchor=true;
            }
            if(equal&&anchor){best=count;bestOffset=offset;}
        }
        if(best==0)return null;
        var merged=new ArrayList<String>();
        for(int k=Math.min(0,bestOffset);k<Math.max(a.size(),bestOffset+b.size());k++)
            merged.add(k>=0&&k<a.size()?a.get(k):b.get(k-bestOffset));
        return String.join("\n",merged);
    }
    private static String normalize(String value){return value.trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
}
