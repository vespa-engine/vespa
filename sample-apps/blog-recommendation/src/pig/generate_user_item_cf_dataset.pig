-- Load data from any source - here we load using PigStorage
data = LOAD 'blog-recommendation/trainPostsFinal' USING JsonLoader('date_gmt:chararray, language:chararray, author:chararray, url:chararray, title:chararray, blog:chararray, post_id:chararray, tags:{T:(tag_name:chararray)}, blogname:chararray, date:chararray, content:chararray, categories:{T:(category_name:chararray)}, likes:{T:(dt:chararray, uid:chararray)}');

data_likes = FOREACH data GENERATE post_id, FLATTEN(likes) AS (dt, uid);

data_cf = FOREACH data_likes GENERATE uid, post_id, 1 as rate;

data_cf = FILTER data_cf BY (uid IS NOT NULL) AND (uid != '') AND (post_id IS NOT NULL) AND (post_id != ''); 

-- data_cf_sample = SAMPLE data_cf 0.001;

-- data_cf = LIMIT data_cf 10;

STORE data_cf INTO 'blog-recommendation/trainPostsFinal_user_item_cf';

